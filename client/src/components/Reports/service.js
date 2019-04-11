/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, del, post} from 'request';

export async function isSharingEnabled() {
  const response = await get(`api/share/isEnabled`);
  const json = await response.json();
  return json.enabled;
}

export async function shareReport(reportId) {
  const body = {
    reportId
  };
  const response = await post(`api/share/report`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedReport(reportId) {
  const response = await get(`api/share/report/${reportId}`);

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeReportSharing(id) {
  return await del(`api/share/report/${id}`);
}
