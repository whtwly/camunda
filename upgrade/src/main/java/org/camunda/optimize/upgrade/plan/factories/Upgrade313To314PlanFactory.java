/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.db.es.schema.index.SettingsIndexES;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

public class Upgrade313To314PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion("3.13")
        .toVersion("3.14.0")
        .addUpgradeStep(new DeleteIndexIfExistsStep("onboarding-state", 2))
        .addUpgradeStep(deleteLastModifierAndTelemetryInitializedSettingFields())
        .build();
  }

  private static UpdateIndexStep deleteLastModifierAndTelemetryInitializedSettingFields() {
    return new UpdateIndexStep(
        new SettingsIndexES(),
        "ctx._source.remove(\"metadataTelemetryEnabled\"); ctx._source.remove(\"lastModifier\");");
  }
}
