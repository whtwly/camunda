/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.camunda.exporter.rdbms.RdbmsExporter;
import io.camunda.qa.util.cluster.TestClient;
import io.camunda.qa.util.cluster.TestRestV2ApiClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.oracle.OracleContainer;

@ZeebeIntegration
public class StandaloneCamundaWithRdbmsOracleTest extends AbstractStandaloneCamundaTest {

  private static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-free:slim-faststart")
          .withUsername("camunda")
          .withPassword("demo")
          //TODO ... DIRTY!!! Find another way to get the random port from container on startup
          .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
              new HostConfig().withPortBindings(
                  new PortBinding(
                      Ports.Binding.bindPort(11521),
                      new ExposedPort(1521)
                  )
              )
          ));

  @TestZeebe
  final TestStandaloneCamunda testStandaloneCamunda =
      TestStandaloneCamunda.withRdbms()
          .withProperty("camunda.database.type", "rdbms")
          .withProperty("spring.datasource.url", "jdbc:oracle:thin:@localhost:11521/FREEPDB1")
          .withProperty("spring.datasource.username", "camunda")
          .withProperty("spring.datasource.password", "demo")
          .withProperty("mybatis.mapper-locations", "classpath:mapper/**/*-mapper.xml")
          .withExporter("rdbms", cfg -> cfg.setClassName(RdbmsExporter.class.getName()));

  @BeforeAll
  static void setUp() {
    ORACLE.start();
  }

  @AfterAll
  static void tearDown() {
    ORACLE.stop();
  }

  @Override
  TestStandaloneCamunda getTestStandaloneCamunda() {
    return testStandaloneCamunda;
  }

  @Override
  TestRestV2ApiClient getTestClient() {
    return testStandaloneCamunda.newRestV2ApiClient();
  }
}
