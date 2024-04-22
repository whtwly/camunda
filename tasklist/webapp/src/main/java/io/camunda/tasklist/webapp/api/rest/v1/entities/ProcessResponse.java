/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.ProcessEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Objects;

public class ProcessResponse {

  @Schema(description = "The unique identifier of the process")
  private String id;

  @Schema(description = "The name of the process")
  private String name;

  @Schema(description = "The BPMN process ID")
  private String bpmnProcessId;

  @Schema(
      description =
          "Array of values to be copied into `ProcessSearchRequest` to request for next or previous page of processes")
  private String[] sortValues;

  @Schema(description = "The version of the process")
  private Integer version;

  @Schema(description = "The ID of the form associated with the start event. Null if not set.")
  private String startEventFormId = null;

  @Schema(description = "The tenant ID associated with the process")
  private String tenantId;

  @Schema(description = "The xml that represents the BPMN for the process")
  private String bpmnXml;

  public String getId() {
    return id;
  }

  public ProcessResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessResponse setName(String name) {
    this.name = name;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessResponse setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getStartEventFormId() {
    return startEventFormId;
  }

  public ProcessResponse setStartEventFormId(String startEventFormId) {
    this.startEventFormId = startEventFormId;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public ProcessResponse setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public ProcessResponse setVersion(Integer version) {
    this.version = version;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessResponse setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getBpmnXml() {
    return bpmnXml;
  }

  public ProcessResponse setBpmnXml(final String bpmnXml) {
    this.bpmnXml = bpmnXml;
    return this;
  }

  public static ProcessResponse fromProcessEntity(ProcessEntity process, String startEventFormId) {
    return new ProcessResponse()
        .setId(process.getId())
        .setName(process.getName())
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setStartEventFormId(startEventFormId)
        .setTenantId(process.getTenantId())
        .setBpmnXml(process.getBpmnXml());
  }

  public static ProcessResponse fromProcessEntityWithoutBpmnXml(
      ProcessEntity process, String startEventFormId) {
    process.setBpmnXml(null);
    return fromProcessEntity(process, startEventFormId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessResponse that = (ProcessResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.deepEquals(sortValues, that.sortValues)
        && Objects.equals(version, that.version)
        && Objects.equals(startEventFormId, that.startEventFormId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(bpmnXml, that.bpmnXml);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        name,
        bpmnProcessId,
        Arrays.hashCode(sortValues),
        version,
        startEventFormId,
        tenantId,
        bpmnXml);
  }
}
