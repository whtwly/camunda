/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

export function isDurationReport(report) {
  return report?.data?.view?.properties.includes('duration');
}

export async function evaluateReport(payload, filter = [], query = {}) {
  let response;

  if (typeof payload !== 'object') {
    // evaluate saved report
    response = await post(`api/report/${payload}/evaluate`, {filter}, {query});
  } else {
    // evaluate unsaved report
    response = await post(`api/report/evaluate/`, payload, {query});
  }

  return await response.json();
}

export function getReportResult(report, idx = 0) {
  if (report?.result?.measures) {
    return {
      ...report.result,
      data: report?.result?.measures?.[idx].data,
    };
  }

  return report.result;
}

export async function loadRawData(config) {
  const response = await post('api/export/csv/process/rawData/data', config);

  return await response.blob();
}
