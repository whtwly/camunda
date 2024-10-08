/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

/**
 * This processor decorates processors where authorization checks are required, taking care of the
 * authorization checks for them. If the authorization check fails, the processor will reject the
 * command and write a rejection response back to the client.
 *
 * <p>This class should only be used by commands that don't need to be distributed. If you have a
 * command which requires distribution the {@link AuthorizableDistributionProcessor} should be used.
 */
public final class AuthorizableProcessor<T extends UnifiedRecordValue, Resource>
    implements TypedRecordProcessor<T> {

  public static final String UNAUTHORIZED_ERROR_MESSAGE =
      "Unauthorized to perform operation '%s' on resource '%s'";
  private final AuthorizationCheckBehavior authorizationCheckBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final Authorizable<T, Resource> delegate;

  public AuthorizableProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final EngineConfiguration engineConfig,
      final Authorizable<T, Resource> delegate) {
    authorizationCheckBehavior =
        new AuthorizationCheckBehavior(
            processingState.getAuthorizationState(), processingState.getUserState(), engineConfig);
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.delegate = delegate;
  }

  @Override
  public void processRecord(final TypedRecord<T> command) {
    final var authorizationRequest = delegate.getAuthorizationRequest(command);

    authorizationRequest.ifRightOrLeft(
        request -> checkAuthorizations(command, request),
        rejection -> {
          rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
          responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
        });
  }

  @Override
  public ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
    return delegate.tryHandleError(command, error);
  }

  private void checkAuthorizations(
      final TypedRecord<T> command, final AuthorizationRequest<Resource> request) {
    if (authorizationCheckBehavior.isAuthorized(command, request)) {
      delegate.processRecord(command, request.getResource().orElseThrow());
    } else {
      final var errorMessage =
          UNAUTHORIZED_ERROR_MESSAGE.formatted(
              request.getPermissionType(), request.getResourceType());
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
    }
  }

  public interface Authorizable<T extends UnifiedRecordValue, Resource> {
    Either<Rejection, AuthorizationRequest<Resource>> getAuthorizationRequest(
        final TypedRecord<T> command);

    void processRecord(final TypedRecord<T> command, final Resource resource);

    /**
     * Try to handle an error that occurred during processing.
     *
     * @param command The command that was being processed when the error occurred
     * @param error The error that occurred, and the processor should attempt to handle
     * @return The type of the processing error. Default: {@link ProcessingError#UNEXPECTED_ERROR}.
     */
    default ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
      return ProcessingError.UNEXPECTED_ERROR;
    }
  }
}
