/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.auth.impl.TenantAuthorizationCheckerImpl;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.AuthorizableProcessor.Authorizable;
import io.camunda.zeebe.engine.processing.streamprocessor.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public final class ProcessInstanceCancelProcessor
    implements Authorizable<ProcessInstanceRecord, ElementInstance> {

  private static final String MESSAGE_PREFIX =
      "Expected to cancel a process instance with key '%d', but ";

  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";

  private static final String PROCESS_NOT_ROOT_MESSAGE =
      MESSAGE_PREFIX
          + "it is created by a parent process instance. Cancel the root process instance '%d' instead.";

  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;

  public ProcessInstanceCancelProcessor(
      final ProcessingState processingState, final Writers writers) {
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
  }

  @Override
  public Either<Rejection, AuthorizationRequest<ElementInstance>> getAuthorizationRequest(
      final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    if (elementInstance == null) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND, String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey())));
    }

    final var authorizationRequest =
        new AuthorizationRequest<ElementInstance>(
                AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .setResource(elementInstance)
            .addResourceId(elementInstance.getValue().getBpmnProcessId());
    return Either.right(authorizationRequest);
  }

  @Override
  public void processRecord(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {
    if (!validateCommand(command, elementInstance)) {
      return;
    }

    final ProcessInstanceRecord value = elementInstance.getValue();

    commandWriter.appendFollowUpCommand(
        command.getKey(), ProcessInstanceIntent.TERMINATE_ELEMENT, value);
    responseWriter.writeEventOnCommand(
        command.getKey(), ProcessInstanceIntent.ELEMENT_TERMINATING, value, command);
  }

  private boolean validateCommand(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {

    if (!elementInstance.canTerminate() || elementInstance.getParentKey() > 0) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      return false;
    }

    if (!TenantAuthorizationCheckerImpl.fromAuthorizationMap(command.getAuthorizations())
        .isAuthorized(elementInstance.getValue().getTenantId())) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      return false;
    }

    final var parentProcessInstanceKey = elementInstance.getValue().getParentProcessInstanceKey();
    if (parentProcessInstanceKey > 0) {

      final var rootProcessInstanceKey = getRootProcessInstanceKey(parentProcessInstanceKey);

      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          String.format(PROCESS_NOT_ROOT_MESSAGE, command.getKey(), rootProcessInstanceKey));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.INVALID_STATE,
          String.format(PROCESS_NOT_ROOT_MESSAGE, command.getKey(), rootProcessInstanceKey));
      return false;
    }

    return true;
  }

  private long getRootProcessInstanceKey(final long instanceKey) {

    final var instance = elementInstanceState.getInstance(instanceKey);
    if (instance != null) {

      final var parentProcessInstanceKey = instance.getValue().getParentProcessInstanceKey();
      if (parentProcessInstanceKey > 0) {

        return getRootProcessInstanceKey(parentProcessInstanceKey);
      }
    }
    return instanceKey;
  }
}
