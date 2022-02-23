/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.report;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DYNAMIC_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_TEXT;

public class SingleProcessReportIndex extends AbstractReportIndex {

  public static final int VERSION = 9;

  @Override
  public String getIndexName() {
    return SINGLE_PROCESS_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder.
      startObject(DATA)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .field(DYNAMIC_PROPERTY_TYPE, true)
        .startObject("properties")
          .startObject(ProcessReportDataDto.Fields.view)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(ProcessReportDataDto.Fields.groupBy)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(ProcessReportDataDto.Fields.distributedBy)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(ProcessReportDataDto.Fields.filter)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(CONFIGURATION)
            .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
            .field(DYNAMIC_PROPERTY_TYPE, true)
            .startObject("properties")
              .startObject(XML)
                .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
                .field("index", true)
                .field("analyzer", "is_present_analyzer")
              .endObject()
              .startObject(AGGREGATION_TYPES)
                .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
                .field(DYNAMIC_PROPERTY_TYPE, true)
              .endObject()
            .endObject()
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }
}
