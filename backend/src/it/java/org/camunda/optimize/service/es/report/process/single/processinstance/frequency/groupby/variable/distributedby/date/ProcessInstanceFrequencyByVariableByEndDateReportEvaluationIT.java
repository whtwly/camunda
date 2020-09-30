/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.variable.distributedby.date;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.test.util.ProcessReportDataType;

import java.time.OffsetDateTime;

public class ProcessInstanceFrequencyByVariableByEndDateReportEvaluationIT
  extends AbstractProcessInstanceFrequencyByVariableByInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_END_DATE;
  }

  @Override
  protected DistributedByType getDistributeByType() {
    return DistributedByType.END_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(final String processInstanceId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, newDate);
  }
}
