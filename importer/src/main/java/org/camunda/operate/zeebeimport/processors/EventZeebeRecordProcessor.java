/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static org.camunda.operate.entities.EventType.ELEMENT_ACTIVATING;
import static org.camunda.operate.entities.EventType.ELEMENT_COMPLETING;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventMetadataEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebe.record.RecordImpl;
import org.camunda.operate.zeebe.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebe.record.value.JobRecordValueImpl;
import org.camunda.operate.zeebe.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EventZeebeRecordProcessor {


  private static final Logger logger = LoggerFactory.getLogger(EventZeebeRecordProcessor.class);

  private static final String ID_PATTERN = "%s_%s";

  private static final Set<String> INCIDENT_EVENTS = new HashSet<>();
  private final static Set<String> JOB_EVENTS = new HashSet<>();
  private static final Set<String> WORKFLOW_INSTANCE_STATES = new HashSet<>();

  static {
    INCIDENT_EVENTS.add(IncidentIntent.CREATED.name());
    INCIDENT_EVENTS.add(IncidentIntent.RESOLVED.name());

    JOB_EVENTS.add(JobIntent.CREATED.name());
    JOB_EVENTS.add(JobIntent.ACTIVATED.name());
    JOB_EVENTS.add(JobIntent.COMPLETED.name());
    JOB_EVENTS.add(JobIntent.TIMED_OUT.name());
    JOB_EVENTS.add(JobIntent.FAILED.name());
    JOB_EVENTS.add(JobIntent.RETRIES_UPDATED.name());
    JOB_EVENTS.add(JobIntent.CANCELED.name());

    WORKFLOW_INSTANCE_STATES.add(ELEMENT_ACTIVATING.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_ACTIVATED.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_COMPLETING.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_TERMINATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private EventTemplate eventTemplate;

  public void processIncidentRecords(Map<Long, List<RecordImpl<IncidentRecordValueImpl>>> records, BulkRequest bulkRequest) throws PersistenceException {
    for (Map.Entry<Long, List<RecordImpl<IncidentRecordValueImpl>>> wiRecordsEntry: records.entrySet()) {
      //we need only last event of the processed type
      List<RecordImpl<IncidentRecordValueImpl>> incidentRecords = wiRecordsEntry.getValue();
      if (incidentRecords.size() >= 1) {
        for (int i = incidentRecords.size() - 1; i>=0; i-- ) {
          final String intentStr = incidentRecords.get(i).getIntent().name();
          if (INCIDENT_EVENTS.contains(intentStr)) {
            IncidentRecordValueImpl recordValue = incidentRecords.get(i).getValue();
            processIncident(incidentRecords.get(i), recordValue, bulkRequest);
            break;
          }
        }
      }
    }
  }

  public void processJobRecords(Map<Long, List<RecordImpl<JobRecordValueImpl>>> records, BulkRequest bulkRequest) throws PersistenceException {
    for (Map.Entry<Long, List<RecordImpl<JobRecordValueImpl>>> wiRecordsEntry: records.entrySet()) {
      //we need only last event of the processed type
      List<RecordImpl<JobRecordValueImpl>> jobRecords = wiRecordsEntry.getValue();
      if (jobRecords.size() >= 1) {
        for (int i = jobRecords.size() - 1; i>=0; i-- ) {
          final String intentStr = jobRecords.get(i).getIntent().name();
          if (JOB_EVENTS.contains(intentStr)) {
            JobRecordValueImpl recordValue = jobRecords.get(i).getValue();
            processJob(jobRecords.get(i), recordValue, bulkRequest);
            break;
          }
        }
      }
    }
  }

  public void processWorkflowInstanceRecords(Map<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> records, BulkRequest bulkRequest) throws PersistenceException {
    for (Map.Entry<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> wiRecordsEntry: records.entrySet()) {
      //we need only last event of the processed type
      List<RecordImpl<WorkflowInstanceRecordValueImpl>> wiRecords = wiRecordsEntry.getValue();
      if (wiRecords.size() >= 1) {
        for (int i = wiRecords.size() - 1; i>=0; i-- ) {
          final String intentStr = wiRecords.get(i).getIntent().name();
          if (WORKFLOW_INSTANCE_STATES.contains(intentStr)) {
            WorkflowInstanceRecordValueImpl recordValue = wiRecords.get(i).getValue();
            processWorkflowInstance(wiRecords.get(i), recordValue, bulkRequest);
            break;
          }
        }
      }
    }
  }

  private void processWorkflowInstance(Record record, WorkflowInstanceRecordValueImpl recordValue, BulkRequest bulkRequest)
    throws PersistenceException {
    if (!isProcessEvent(recordValue)) {   //we do not need to store process level events
      EventEntity eventEntity = new EventEntity();

      eventEntity.setId(String.format(ID_PATTERN, recordValue.getWorkflowInstanceKey(), record.getKey()));

      loadEventGeneralData(record, eventEntity);

      eventEntity.setWorkflowKey(recordValue.getWorkflowKey());
      eventEntity.setWorkflowInstanceKey(recordValue.getWorkflowInstanceKey());
      eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

      if (recordValue.getElementId() != null) {
        eventEntity.setActivityId(recordValue.getElementId());
      }

      if (record.getKey() != recordValue.getWorkflowInstanceKey()) {
        eventEntity.setFlowNodeInstanceKey(record.getKey());
      }

      persistEvent(eventEntity, bulkRequest);
    }
  }

  private void processJob(Record record, JobRecordValueImpl recordValue, BulkRequest bulkRequest) throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    eventEntity.setId(String.format(ID_PATTERN, recordValue.getWorkflowInstanceKey(), recordValue.getElementInstanceKey()));

    loadEventGeneralData(record, eventEntity);

    final long workflowKey = recordValue.getWorkflowKey();
    if (workflowKey > 0) {
      eventEntity.setWorkflowKey(workflowKey);
    }

    final long workflowInstanceKey = recordValue.getWorkflowInstanceKey();
    if (workflowInstanceKey > 0) {
      eventEntity.setWorkflowInstanceKey(workflowInstanceKey);
    }

    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

    eventEntity.setActivityId(recordValue.getElementId());

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      eventEntity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setJobType(recordValue.getType());
    eventMetadata.setJobRetries(recordValue.getRetries());
    eventMetadata.setJobWorker(recordValue.getWorker());
    eventMetadata.setJobCustomHeaders(recordValue.getCustomHeaders());

    if (record.getKey() > 0) {
      eventMetadata.setJobKey(record.getKey());
    }

    long jobDeadline = recordValue.getDeadline();
    if (jobDeadline >= 0) {
      eventMetadata.setJobDeadline(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(jobDeadline)));
    }

    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, bulkRequest);

  }

  private void processIncident(Record record, IncidentRecordValueImpl recordValue, BulkRequest bulkRequest) throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    eventEntity.setId(String.format(ID_PATTERN, recordValue.getWorkflowInstanceKey(), recordValue.getElementInstanceKey()));

    loadEventGeneralData(record, eventEntity);

    if (recordValue.getWorkflowInstanceKey() > 0) {
      eventEntity.setWorkflowInstanceKey(recordValue.getWorkflowInstanceKey());
    }
    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    eventEntity.setActivityId(recordValue.getElementId());
    if (recordValue.getElementInstanceKey() > 0) {
      eventEntity.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setIncidentErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    eventMetadata.setIncidentErrorType(recordValue.getErrorType());
    if (recordValue.getJobKey() > 0) {
      eventMetadata.setJobKey(recordValue.getJobKey());
    }
    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, bulkRequest);
  }

  private boolean isProcessEvent(WorkflowInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(WorkflowInstanceRecordValueImpl recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

  private void loadEventGeneralData(Record record, EventEntity eventEntity) {
//    eventEntity.setId(String.valueOf(record.getPosition()));
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getPartitionId());
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(record.getValueType()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getIntent().name()));
  }

  private void persistEvent(EventEntity entity, BulkRequest bulkRequest) throws PersistenceException {
    try {
      logger.debug("Event: id {}, eventSourceType {}, eventType {}, workflowInstanceKey {}", entity.getId(), entity.getEventSourceType(), entity.getEventType(),
        entity.getWorkflowInstanceKey());

      //write event
      bulkRequest.add(new IndexRequest(eventTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .source(objectMapper.writeValueAsString(entity), XContentType.JSON));

    } catch (JsonProcessingException e) {
      logger.error("Error preparing the query to insert event", e);
      throw new PersistenceException(String.format("Error preparing the query to insert event [%s]", entity.getId()), e);
    }
  }

}
