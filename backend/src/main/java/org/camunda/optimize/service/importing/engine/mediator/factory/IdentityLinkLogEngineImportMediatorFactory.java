/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.IdentityLinkLogWriter;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.IdentityLinkLogInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.IdentityLinkLogEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.IdentityLinkLogImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class IdentityLinkLogEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final IdentityLinkLogWriter identityLinkLogWriter;

  public IdentityLinkLogEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                    final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                    final ConfigurationService configurationService,
                                                    final IdentityLinkLogWriter identityLinkLogWriter) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.identityLinkLogWriter = identityLinkLogWriter;
  }

  @Override
  public List<EngineImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportUserTaskWorkerDataEnabled() ?
      ImmutableList.of(createIdentityLinkLogEngineImportMediator(engineContext))
      : Collections.emptyList();
  }

  public IdentityLinkLogEngineImportMediator createIdentityLinkLogEngineImportMediator(
    final EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new IdentityLinkLogEngineImportMediator(
      importIndexHandlerRegistry.getIdentityLinkImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(IdentityLinkLogInstanceFetcher.class, engineContext),
      new IdentityLinkLogImportService(
        identityLinkLogWriter,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
