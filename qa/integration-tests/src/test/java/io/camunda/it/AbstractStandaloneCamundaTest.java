/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestClient;
import io.camunda.qa.util.cluster.TestClient.ProcessInstanceResult;
import io.camunda.qa.util.cluster.TestRestV2ApiClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

abstract class AbstractStandaloneCamundaTest {

  abstract TestStandaloneCamunda getTestStandaloneCamunda();

  abstract TestRestV2ApiClient getTestClient();

  @Test
  public void shouldCreateAndRetrieveInstance() {
    // givne
    final var zeebeClient = getTestStandaloneCamunda().newClientBuilder().build();

    // when
    zeebeClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("test")
                .zeebeJobType("type")
                .endEvent()
                .done(),
            "simple.bpmn")
        .send()
        .join();

    final var processInstanceEvent =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    final var testClient = getTestClient();
    Awaitility.await("should receive data from ES")
        .timeout(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final Either<Exception, ProcessInstanceSearchQueryResponse> eitherProcessInstanceResult =
                  testClient.getProcessInstanceWith(processInstanceEvent.getProcessInstanceKey());

              if (eitherProcessInstanceResult.isLeft()) {
                System.out.println(eitherProcessInstanceResult.getLeft().getMessage());
              }

              // has no exception
              assertThat(eitherProcessInstanceResult.isRight())
                  .withFailMessage("Expect no error on retrieving process instance")
                  .isTrue();

              final var processInstanceResult = eitherProcessInstanceResult.get();

              long total = processInstanceResult.getPage().getTotalItems();
              assertThat(total)
                  .withFailMessage("Expect to read a process instance from RDBMS")
                  .isGreaterThan(0);

              assertThat(processInstanceResult.getItems().getFirst().getProcessInstanceKey())
                  .withFailMessage("Expect to read the expected process instance from RDBMS")
                  .isEqualTo(processInstanceEvent.getProcessInstanceKey());
            });
  }
}
