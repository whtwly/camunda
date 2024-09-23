/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAssignProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskClaimProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCompleteProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskUpdateProcessor;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class UserTaskCommandProcessors {

  private final Map<UserTaskIntent, UserTaskCommandProcessor> commandToProcessor;

  public UserTaskCommandProcessors(
      final MutableProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers) {
    final var keyGenerator = processingState.getKeyGenerator();
    final EventHandle eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processingState.getProcessState(),
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());

    commandToProcessor =
        new EnumMap<>(
            Map.of(
                UserTaskIntent.ASSIGN,
                new UserTaskAssignProcessor(processingState, writers),
                UserTaskIntent.CLAIM,
                new UserTaskClaimProcessor(processingState, writers),
                UserTaskIntent.UPDATE,
                new UserTaskUpdateProcessor(processingState, writers),
                UserTaskIntent.COMPLETE,
                new UserTaskCompleteProcessor(processingState, eventHandle, writers)));
    validateProcessorsSetup(commandToProcessor);
  }

  public UserTaskCommandProcessor getCommandProcessor(UserTaskIntent userTaskIntent) {
    if (userTaskIntent.isEvent()) {
      throw new IllegalArgumentException(
          "Expected a command, but received an event: '%s'. Valid UserTask commands are: %s"
              .formatted(userTaskIntent, UserTaskIntent.commands()));
    }

    return Optional.ofNullable(commandToProcessor.get(userTaskIntent))
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "No processor found for the '%s' UserTask command".formatted(userTaskIntent)));
  }

  private static void validateProcessorsSetup(
      Map<UserTaskIntent, UserTaskCommandProcessor> commandToProcessor) {
    final var missingProcessors =
        UserTaskIntent.commands().stream()
            // Exclude COMPLETE_TASK_LISTENER as it doesn't require a dedicated processor.
            // This intent is handled internally within UserTaskProcessor
            .filter(intent -> intent != UserTaskIntent.COMPLETE_TASK_LISTENER)
            .filter(intent -> !commandToProcessor.containsKey(intent))
            .collect(Collectors.toSet());

    if (!missingProcessors.isEmpty()) {
      throw new IllegalStateException(
          "No processors defined for the following UserTask commands: %s"
              .formatted(missingProcessors));
    }
  }
}
