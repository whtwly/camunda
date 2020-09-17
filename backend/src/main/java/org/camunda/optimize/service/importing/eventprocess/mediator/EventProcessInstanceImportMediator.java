/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessEventDto;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.eventprocess.handler.EventProcessInstanceImportSourceIndexHandler;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class EventProcessInstanceImportMediator<T extends EventProcessEventDto>
  extends TimestampBasedImportMediator<EventProcessInstanceImportSourceIndexHandler, T> {

  @Getter
  private final String publishedProcessStateId;
  private final EventFetcherService eventFetcherService;
  private final ConfigurationService configurationService;

  public EventProcessInstanceImportMediator(final String publishedProcessStateId,
                                            final EventProcessInstanceImportSourceIndexHandler importSourceIndexHandler,
                                            final EventFetcherService eventFetcherService,
                                            final ImportService<T> eventProcessEventImportService,
                                            final ConfigurationService configurationService,
                                            final BackoffCalculator idleBackoffCalculator) {
    this.publishedProcessStateId = publishedProcessStateId;
    this.importIndexHandler = importSourceIndexHandler;
    this.eventFetcherService = eventFetcherService;
    this.importService = eventProcessEventImportService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected OffsetDateTime getTimestamp(final T eventProcessEventDto) {
    if (eventProcessEventDto instanceof EventDto) {
      return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(((EventDto) eventProcessEventDto).getIngestionTimestamp()),
        ZoneId.systemDefault()
      );
    } else if (eventProcessEventDto instanceof CamundaActivityEventDto) {
      return ((CamundaActivityEventDto) eventProcessEventDto).getTimestamp();
    } else {
      throw new OptimizeRuntimeException("Cannot read import timestamp for unsupported entity");
    }
  }

  @Override
  protected List<T> getEntitiesNextPage() {
    return eventFetcherService.getEventsIngestedAfter(
      importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli(),
      getMaxPageSize()
    );
  }

  @Override
  protected List<T> getEntitiesLastTimestamp() {
    return eventFetcherService.getEventsIngestedAt(
      importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli()
    );
  }

  @Override
  public int getMaxPageSize() {
    return configurationService.getEventImportConfiguration().getMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE;
  }

}
