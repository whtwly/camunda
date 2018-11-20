package org.camunda.optimize.service.es.report.pi.duration.groupby.date;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataBuilder;
import org.camunda.optimize.test.util.ReportDataType;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.test.util.VariableFilterUtilHelper.createBooleanVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class ProcessInstanceDurationByStartDateReportEvaluationIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String TEST_ACTIVITY = "testActivity";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  @Test
  @Parameters(method = "reportDataTypeAndViewOperationProvider")
  public void simpleReportEvaluation(ReportDataType reportDataType, ProcessViewOperation operation) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setReportDataType(reportDataType)
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(1000L));
  }

  private Object[] reportDataTypeAndViewOperationProvider() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_START_DATE, ProcessViewOperation.AVG},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_START_DATE, ProcessViewOperation.MIN},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_START_DATE, ProcessViewOperation.MAX},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE, ProcessViewOperation.MEDIAN}
    };
  }

  @Test
  @Parameters(method = "reportDataTypeAndViewOperationProvider")
  public void simpleReportEvaluationById(ReportDataType reportDataType, ProcessViewOperation operation) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    MapProcessReportResultDto result = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();

    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(1000L));
  }

  @Test
  @Parameters
  public void processInstancesStartedAtSameIntervalAreGroupedTogether(ReportDataType reportDataType,
                                                                      long expectedToday,
                                                                      long expectedYesterday) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 9L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 1L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(processDefinitionVersion)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    OffsetDateTime startOfToday = startDate.truncatedTo(ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(expectedToday));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.containsKey(expectedStringYesterday), is(true));
    assertThat(resultMap.get(expectedStringYesterday), is(expectedYesterday));
  }

  private Object[] parametersForProcessInstancesStartedAtSameIntervalAreGroupedTogether() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_START_DATE, 4000L, 1000L},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_START_DATE, 1000L, 1000L},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_START_DATE, 9000L, 1000L},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE, 2000L, 1000L}
    };
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void resultIsSortedInDescendingOrder(ReportDataType reportDataType) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, -2L, 3L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 1L);


    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(processDefinitionVersion)
            .setReportDataType(reportDataType)
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(3));
    assertThat(new ArrayList<>(resultMap.keySet()), isInDescendingOrdering());
  }

  private Matcher<? super List<String>> isInDescendingOrdering()
  {
    return new TypeSafeMatcher<List<String>>()
    {
      @Override
      public void describeTo (Description description)
      {
        description.appendText("The given list should be sorted in descending order!");
      }

      @Override
      protected boolean matchesSafely (List<String> item)
      {
        for(int i = (item.size() - 1) ; i > 0; i--) {
          if(item.get(i).compareTo(item.get(i-1)) > 0) return false;
        }
        return true;
      }
    };
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) throws SQLException {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceId, shiftedStartDate.plusSeconds(durationInSec));
  }

  @Test
  @Parameters
  public void emptyIntervalBetweenTwoProcessInstances(ReportDataType reportDataType,
                                                      long expectedTodayDuration) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 9L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -2L, 1L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionVersion(processDefinitionVersion)
            .setProcessDefinitionKey(processDefinitionKey)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(3));

    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);

    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(expectedTodayDuration));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.containsKey(expectedStringYesterday), is(true));
    assertThat(resultMap.get(expectedStringYesterday), is(0L));
    String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultMap.containsKey(expectedStringDayBeforeYesterday), is(true));
    assertThat(resultMap.get(expectedStringDayBeforeYesterday), is(1000L));
  }

  private Object[] parametersForEmptyIntervalBetweenTwoProcessInstances() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_START_DATE, 4000L},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_START_DATE, 1000L},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_START_DATE, 9000L},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE, 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByHour(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.HOURS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setDateInterval(GroupByDateUnit.HOUR)
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 5, now, ChronoUnit.HOURS);
  }

  private void assertDateResultMap(Map<String, Long> resultMap, int size, OffsetDateTime now, ChronoUnit unit) {
    assertThat(resultMap.size(), is(size));
    final OffsetDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultMap.containsKey(expectedDateString), is(true));
        assertThat(resultMap.get(expectedDateString), is(1000L));
      });
  }

  private OffsetDateTime truncateToStartOfUnit(OffsetDateTime date, ChronoUnit unit) {
    OffsetDateTime truncatedDate;
    if (unit.equals(ChronoUnit.HOURS) || unit.equals(ChronoUnit.DAYS)) {
      truncatedDate = date.truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.WEEKS)) {
      truncatedDate = date.with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
    } else if (unit.equals(ChronoUnit.MONTHS)){
      truncatedDate = date.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    } else {
      // it should be year
      truncatedDate = date.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
    }
    return truncatedDate;
  }

  private void updateProcessInstancesDates(List<ProcessInstanceEngineDto> procInsts,
                                           OffsetDateTime now,
                                           ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    Map<String, OffsetDateTime> idToNewEndDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach( i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
        idToNewEndDate.put(id, newStartDate.plusSeconds(1L));
      });
    engineDatabaseRule.updateProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseRule.updateProcessInstanceEndDates(idToNewEndDate);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByDay(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
            .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
            .setDateInterval(GroupByDateUnit.DAY)
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.DAYS);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByWeek(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setDateInterval(GroupByDateUnit.WEEK)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.WEEKS);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByMonth(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setDateInterval(GroupByDateUnit.MONTH)
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.MONTHS);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByYear(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setDateInterval(GroupByDateUnit.YEAR)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.YEARS);
  }

  @Test
  @Parameters
  public void reportAcrossAllVersions(ReportDataType reportDataType,
                                      long expectedDuration) throws Exception {
    //given
    OffsetDateTime startDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusDays(2);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 2L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    // when
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = startDate.truncatedTo(ChronoUnit.DAYS);
    String expectedStartDateString = localDateTimeToString(startOfToday);
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(expectedStartDateString), is(true));
    assertThat(resultMap.get(expectedStartDateString), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_START_DATE, 1500L},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_START_DATE, 1000L},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_START_DATE, 2000L}
    };
  }

  @Test
  @Parameters
  public void otherProcessDefinitionsDoNoAffectResult(ReportDataType reportDataType,
                                                      long expectedDuration) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusDays(2);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder.createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = startDate.truncatedTo(ChronoUnit.DAYS);
    String expectedStartDateString = localDateTimeToString(startOfToday);
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(expectedStartDateString), is(true));
    assertThat(resultMap.get(expectedStartDateString), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
            new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_START_DATE, 1000L},
            new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_START_DATE, 1000L},
            new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_START_DATE, 1000L}
    };
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void filterInReportWorks(ReportDataType reportDataType) throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), startDate, 0L, 1L);
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setReportDataType(reportDataType)
            .setFilter(createVariableFilter("var", "true"))
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    // when
    reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setReportDataType(reportDataType)
            .setFilter(createVariableFilter("var", "false"))
            .build();

    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));
  }

  private List<ProcessFilterDto> createVariableFilter(String varName, String value) {
    VariableFilterDto variableFilterDto = createBooleanVariableFilter(varName, value);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void optimizeExceptionOnGroupByTypeIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setProcessDefinitionVersion("1")
            .setDateInterval(GroupByDateUnit.DAY)
            .setReportDataType(reportDataType)
            .build();

    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void optimizeExceptionOnGroupByValueIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setProcessDefinitionVersion("1")
            .setDateInterval(GroupByDateUnit.DAY)
            .setReportDataType(reportDataType)
            .build();

    StartDateGroupByDto groupByDto = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setUnit(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void optimizeExceptionOnGroupByUnitIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder.createReportData()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setProcessDefinitionVersion("1")
            .setDateInterval(GroupByDateUnit.DAY)
            .setReportDataType(reportDataType)
            .build();
    StartDateGroupByDto groupByStartDate = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByStartDate.setValue(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() throws IOException {
    return deployAndStartSimpleProcesses(1).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number) throws IOException {
    ProcessDefinitionEngineDto processDefinition= deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    return deployAndStartSimpleServiceTaskProcessWithVariables(TEST_ACTIVITY, variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(String activityId,
                                                                                       Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(activityId)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateReportRequest(id, updatedReport)
            .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewReport() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private MapProcessReportResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(MapProcessReportResultDto.class, 200);
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {

    String id = createNewReport();

    SingleReportDefinitionDto<ProcessReportDataDto> report = new SingleReportDefinitionDto<>();
    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName("something");
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private String localDateTimeToString(OffsetDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time.withOffsetSameInstant(ZoneOffset.UTC));
  }


  private MapProcessReportResultDto evaluateReport(ProcessReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapProcessReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

  public static class ReportDataTypeProvider {
    public static Object[] provideReportDataType() {
      return new Object[]{
        new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_START_DATE},
        new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_START_DATE},
        new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_START_DATE},
        new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE}
      };
    }
  }


}
