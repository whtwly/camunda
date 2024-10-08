/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.AuthorizableCommandProcessor.Authorizable;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.processing.streamprocessor.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public final class ProcessInstanceCreationCreateWithResultProcessor
    implements Authorizable<ProcessInstanceCreationRecord, DeployedProcess> {

  private final ProcessInstanceCreationCreateProcessor createProcessor;
  private final MutableElementInstanceState elementInstanceState;
  private final AwaitProcessInstanceResultMetadata awaitResultMetadata =
      new AwaitProcessInstanceResultMetadata();

  private final CommandControlWithAwaitResult wrappedController =
      new CommandControlWithAwaitResult();

  private boolean shouldRespond;

  public ProcessInstanceCreationCreateWithResultProcessor(
      final ProcessInstanceCreationCreateProcessor createProcessor,
      final MutableElementInstanceState elementInstanceState) {
    this.createProcessor = createProcessor;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public Either<Rejection, AuthorizationRequest<DeployedProcess>> getAuthorizationRequest(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final CommandControl<ProcessInstanceCreationRecord> controller) {
    return createProcessor.getAuthorizationRequest(command, controller);
  }

  @Override
  public boolean onCommand(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final DeployedProcess deployedProcess) {
    wrappedController.setCommand(command).setController(controller);
    createProcessor.onCommand(command, wrappedController, deployedProcess);
    return shouldRespond;
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long key,
      final Intent intent,
      final ProcessInstanceCreationRecord value) {
    createProcessor.afterAccept(commandWriter, stateWriter, key, intent, value);
  }

  private final class CommandControlWithAwaitResult
      implements CommandControl<ProcessInstanceCreationRecord> {

    TypedRecord<ProcessInstanceCreationRecord> command;
    CommandControl<ProcessInstanceCreationRecord> controller;

    public CommandControlWithAwaitResult setCommand(
        final TypedRecord<ProcessInstanceCreationRecord> command) {
      this.command = command;
      return this;
    }

    public CommandControlWithAwaitResult setController(
        final CommandControl<ProcessInstanceCreationRecord> controller) {
      this.controller = controller;
      return this;
    }

    @Override
    public long accept(final Intent newState, final ProcessInstanceCreationRecord updatedValue) {
      shouldRespond = false;
      final ArrayProperty<StringValue> fetchVariables = command.getValue().fetchVariables();
      awaitResultMetadata
          .setRequestId(command.getRequestId())
          .setRequestStreamId(command.getRequestStreamId())
          .setFetchVariables(fetchVariables);

      elementInstanceState.setAwaitResultRequestMetadata(
          updatedValue.getProcessInstanceKey(), awaitResultMetadata);
      return controller.accept(newState, updatedValue);
    }

    @Override
    public void reject(final RejectionType type, final String reason) {
      shouldRespond = true;
      controller.reject(type, reason);
    }
  }
}
