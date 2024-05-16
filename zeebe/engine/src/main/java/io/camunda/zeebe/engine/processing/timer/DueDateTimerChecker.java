/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.processing.scheduled.DueDateChecker;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState.TimerVisitor;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DueDateTimerChecker implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(DueDateTimerChecker.class);

  private static final long TIMER_RESOLUTION = Duration.ofMillis(100).toMillis();
  private static final double GIVE_YIELD_FACTOR = 0.5;
  private final DueDateChecker dueDateChecker;

  public DueDateTimerChecker(
      final TimerInstanceState timerInstanceState,
      final FeatureFlags featureFlags,
      final BannedInstanceState bannedInstanceState) {
    dueDateChecker =
        new DueDateChecker(
            TIMER_RESOLUTION,
            featureFlags.enableTimerDueDateCheckerAsync(),
            new TriggerTimersSideEffect(
                timerInstanceState,
                ActorClock.current(),
                bannedInstanceState,
                featureFlags.yieldingDueDateChecker()));
  }

  public void scheduleTimer(final long dueDate) {
    dueDateChecker.schedule(dueDate);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    dueDateChecker.onRecovered(context);
  }

  @Override
  public void onClose() {
    dueDateChecker.onClose();
  }

  @Override
  public void onFailed() {
    dueDateChecker.onFailed();
  }

  @Override
  public void onPaused() {
    dueDateChecker.onPaused();
  }

  @Override
  public void onResumed() {
    dueDateChecker.onResumed();
  }

  protected static final class TriggerTimersSideEffect
      implements Function<TaskResultBuilder, Long> {

    private final ActorClock actorClock;

    private final TimerInstanceState timerInstanceState;
    private final BannedInstanceState bannedInstanceState;
    private final boolean yieldControl;

    public TriggerTimersSideEffect(
        final TimerInstanceState timerInstanceState,
        final ActorClock actorClock,
        final BannedInstanceState bannedInstanceState,
        final boolean yieldControl) {
      this.timerInstanceState = timerInstanceState;
      this.actorClock = actorClock;
      this.bannedInstanceState = bannedInstanceState;
      this.yieldControl = yieldControl;
    }

    @Override
    public Long apply(final TaskResultBuilder taskResultBuilder) {
      final var now = actorClock.getTimeMillis();

      final var yieldAfter = now + Math.round(TIMER_RESOLUTION * GIVE_YIELD_FACTOR);

      final TimerVisitor timerVisitor;
      if (yieldControl) {
        timerVisitor =
            new YieldingDecorator(
                actorClock,
                yieldAfter,
                new WriteTriggerTimerCommandVisitor(
                    taskResultBuilder, bannedInstanceState, timerInstanceState));
      } else {
        timerVisitor =
            new WriteTriggerTimerCommandVisitor(
                taskResultBuilder, bannedInstanceState, timerInstanceState);
      }

      return timerInstanceState.processTimersWithDueDateBefore(now, timerVisitor);
    }
  }

  protected static final class WriteTriggerTimerCommandVisitor implements TimerVisitor {

    private final TimerRecord timerRecord = new TimerRecord();

    private final TaskResultBuilder taskResultBuilder;
    private final BannedInstanceState bannedInstanceState;
    private final TimerInstanceState timerInstanceState;

    public WriteTriggerTimerCommandVisitor(
        final TaskResultBuilder taskResultBuilder,
        final BannedInstanceState bannedInstanceState,
        final TimerInstanceState timerInstanceState) {
      this.taskResultBuilder = taskResultBuilder;
      this.bannedInstanceState = bannedInstanceState;
      this.timerInstanceState = timerInstanceState;
    }

    @Override
    public boolean visit(final TimerInstance timer) {
      timerRecord.reset();
      timerRecord
          .setElementInstanceKey(timer.getElementInstanceKey())
          .setProcessInstanceKey(timer.getProcessInstanceKey())
          .setDueDate(timer.getDueDate())
          .setTargetElementId(timer.getHandlerNodeId())
          .setRepetitions(timer.getRepetitions())
          .setProcessDefinitionKey(timer.getProcessDefinitionKey())
          .setTenantId(timer.getTenantId());

      if (bannedInstanceState.isBanned(timerRecord)) {
        LOG.info("The instance of the timer is banned, removing timer from the state.");
        timerInstanceState.remove(timer);
        return true;
      }

      return taskResultBuilder.appendCommandRecord(
          timer.getKey(), TimerIntent.TRIGGER, timerRecord);
    }
  }

  protected static final class YieldingDecorator implements TimerVisitor {

    private final TimerVisitor delegate;
    private final ActorClock actorClock;
    private final long giveYieldAfter;

    public YieldingDecorator(
        final ActorClock actorClock, final long giveYieldAfter, final TimerVisitor delegate) {
      this.delegate = delegate;
      this.actorClock = actorClock;
      this.giveYieldAfter = giveYieldAfter;
    }

    @Override
    public boolean visit(final TimerInstance timer) {
      if (actorClock.getTimeMillis() >= giveYieldAfter) {
        return false;
      }
      return delegate.visit(timer);
    }
  }
}
