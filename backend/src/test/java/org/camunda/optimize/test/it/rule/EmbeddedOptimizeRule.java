package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.engine.importing.EngineImportJobSchedulerFactory;
import org.camunda.optimize.service.engine.importing.EngineImportJobExecutor;
import org.camunda.optimize.service.engine.importing.EngineImportJobScheduler;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.job.factory.StoreIndexesEngineImportJobFactory;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.util.SynchronizationEngineImportJob;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Helper rule to start embedded jetty with Camunda Optimize on bord.
 *
 * @author Askar Akhmerov
 */
public class EmbeddedOptimizeRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(EmbeddedOptimizeRule.class);

  /**
   * Schedule import of all entities, execute all available jobs sequentially
   * until nothing more exists in scheduler queue.
   */
  public void scheduleAllJobsAndImportEngineEntities() throws OptimizeException {

    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = getElasticsearchImportJobExecutor();
    EngineImportJobExecutor engineImportJobExecutor = getEngineImportJobExecutor();
    engineImportJobExecutor.startExecutingImportJobs();
    elasticsearchImportJobExecutor.startExecutingImportJobs();

    resetImportStartIndexes();

    for (EngineImportJobScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {

      scheduleImportAndWaitUntilIsFinished(scheduler);
      // we need another round for the scroll based import index handlers
      scheduleImportAndWaitUntilIsFinished(scheduler);
    }
  }

  private void scheduleImportAndWaitUntilIsFinished(EngineImportJobScheduler scheduler) {
    scheduler.scheduleUntilCantCreateNewJobs();
    makeSureAllScheduledJobsAreFinished();
  }

  public void storeImportIndexesToElasticsearch() {
    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      StoreIndexesEngineImportJobFactory storeIndexesEngineImportJobFactory = (StoreIndexesEngineImportJobFactory)
              getApplicationContext().getBean(
                  BeanHelper.getBeanName(StoreIndexesEngineImportJobFactory.class),
                  engineAlias
              );
      storeIndexesEngineImportJobFactory.disableBlocking();

      Runnable storeIndexesEngineImportJob =
          storeIndexesEngineImportJobFactory.getNextJob().get();

      try {
        getEngineImportJobExecutor().executeImportJob(storeIndexesEngineImportJob);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      makeSureAllScheduledJobsAreFinished();
    }

  }

  private void makeSureAllScheduledJobsAreFinished() {
    CountDownLatch synchronizationObject = new CountDownLatch(2);
    SynchronizationEngineImportJob synchronizationEngineImportJob =
      new SynchronizationEngineImportJob(getElasticsearchImportJobExecutor(), synchronizationObject);

    try {
      getEngineImportJobExecutor().executeImportJob(synchronizationEngineImportJob);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    try {
      synchronizationObject.countDown();
      synchronizationObject.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Execute one round\job using import scheduler infrastructure
   *
   * NOTE: this method does not invoke scheduling of jobs
   */
  public void importEngineEntitiesRound() throws OptimizeException {
    getElasticsearchImportJobExecutor().startExecutingImportJobs();

    for (EngineImportJobScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.scheduleNextRound();
    }

  }

  public void scheduleImport() {
    for (EngineImportJobScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.scheduleNextRound();
    }
  }

  private EngineImportJobSchedulerFactory getImportSchedulerFactory() {
    return getOptimize().getApplicationContext().getBean(EngineImportJobSchedulerFactory.class);
  }

  private TestEmbeddedCamundaOptimize getOptimize() {
    return TestEmbeddedCamundaOptimize.getInstance();
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getOptimize().getElasticsearchImportJobExecutor();
  }

  public void initializeSchema() {
    getOptimize().initializeSchema();
  }

  public EngineImportJobExecutor getEngineImportJobExecutor() {
    return getOptimize().getEngineImportJobExecutor();
  }

  protected void starting(Description description) {
    startOptimize();
    resetImportStartIndexes();
  }

  public String getAuthenticationToken() {
    return getOptimize().getAuthenticationToken();
  }

  public String authenticateDemo() {
    Response tokenResponse = authenticateDemoRequest();
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateDemoRequest() {
    return authenticateUserRequest("demo", "demo");
  }

  public Response authenticateUserRequest(String username, String password) {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(username);
    entity.setPassword(password);

    return target("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public void startOptimize() {
    try {
      getOptimize().start();
    } catch (Exception e) {
      logger.error("Failed to start Optimize", e);
    }
  }

  protected void finished(Description description) {
    TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
    reloadConfiguration();
  }

  public void reloadConfiguration() {
    getOptimize().reloadConfiguration();
  }

  public void stopOptimize() {
    try {
      getOptimize().destroy();
    } catch (Exception e) {
      logger.error("Failed to stop Optimize", e);
    }
  }

  public final WebTarget target(String path) {
    return getOptimize().target(path);
  }

  public final WebTarget target() {
    return getOptimize().target();
  }

  public final WebTarget rootTarget(String path) {
    return getOptimize().rootTarget(path);
  }

  public final WebTarget rootTarget() {
    return getOptimize().rootTarget();
  }

  public String getProcessDefinitionEndpoint() {
    return getConfigurationService().getProcessDefinitionEndpoint();
  }

  public List<Long> getImportIndexes() {
    List<Long> indexes = new LinkedList<>();

    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      getIndexProvider()
          .getAllEntitiesBasedHandlers(engineAlias)
          .forEach(handler -> indexes.add(handler.getImportIndex()));
      getIndexProvider()
          .getDefinitionBasedHandlers(engineAlias)
          .forEach(handler -> indexes.add(handler.getCurrentDefinitionBasedImportIndex()));
    }

    return indexes;
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedImportIndexHandler() {
    List<DefinitionBasedImportIndexHandler> indexes = new LinkedList<>();
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      if (importIndexHandler instanceof DefinitionBasedImportIndexHandler) {
        indexes.add((DefinitionBasedImportIndexHandler) importIndexHandler);
      }
    }
    return indexes;
  }

  public void resetImportStartIndexes() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.resetImportIndex();
    }
  }

  public long getProgressValue() {
    return this.target()
        .path("status/import-progress")
        .request()
        .get(ProgressDto.class).getProgress();
  }

  public boolean isImporting() {
    return this.getElasticsearchImportJobExecutor().isActive();
  }

  public ApplicationContext getApplicationContext() {
    return getOptimize().getApplicationContext();
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getOptimize().getDateTimeFormatter();
  }

  public int getMaxVariableValueListSize() {
    return getConfigurationService().getMaxVariableValueListSize();
  }

  public ConfigurationService getConfigurationService() {
    return getOptimize().getConfigurationService();
  }

  /**
   * In case the engine got new entities, e.g., process definitions, those are then added to the import index
   */
  public void updateImportIndex() {
    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      for (DefinitionBasedImportIndexHandler importIndexHandler : getIndexProvider().getDefinitionBasedHandlers(engineAlias)) {
        importIndexHandler.updateImportIndex();
      }
    }

  }

  private ImportIndexHandlerProvider getIndexProvider() {
    return getApplicationContext().getBean(ImportIndexHandlerProvider.class);
  }
}
