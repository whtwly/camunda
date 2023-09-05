/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';
import {Paths} from 'modules/Routes';

export class ProcessInstance {
  private page: Page;
  readonly instanceHeader: Locator;
  readonly instanceHistory: Locator;
  readonly variablesList: Locator;
  readonly incidentsTable: Locator;
  readonly incidentsBanner: Locator;
  readonly diagram: Locator;
  readonly popover: Locator;
  readonly variablePanelEmptyText: Locator;
  readonly addVariableButton: Locator;
  readonly saveVariableButton: Locator;
  readonly newVariableNameField: Locator;
  readonly newVariableValueField: Locator;
  readonly editVariableValueField: Locator;
  readonly variableSpinner: Locator;
  readonly operationSpinner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.instanceHeader = page.getByTestId('instance-header');
    this.instanceHistory = page.getByTestId('instance-history');
    this.variablesList = page.getByTestId('variables-list');
    this.incidentsTable = page.getByTestId('data-list');
    this.incidentsBanner = page.getByTestId('incidents-banner');
    this.diagram = page.getByTestId('diagram');
    this.popover = page.getByTestId('popover');
    this.variablePanelEmptyText = page.getByText(
      /to view the variables, select a single flow node instance in the instance history./i,
    );
    this.addVariableButton = page.getByRole('button', {name: 'Add variable'});
    this.saveVariableButton = page.getByRole('button', {name: 'Save variable'});
    this.newVariableNameField = page.getByRole('textbox', {name: 'Name'});
    this.newVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.editVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.variableSpinner = page.getByTestId('variable-operation-spinner');
    this.operationSpinner = page.getByTestId('operation-spinner');
  }

  getEditVariableFieldSelector(variableName: string) {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByRole('textbox', {
        name: /value/i,
      });
  }

  getNewVariableNameFieldSelector = (variableName: string) => {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByTestId('new-variable-name');
  };

  getNewVariableValueFieldSelector = (variableName: string) => {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByTestId('new-variable-value');
  };

  async undoModification() {
    await this.page
      .getByRole('button', {
        name: /undo/i,
      })
      .click();
  }

  async navigateToProcessInstance({
    id,
    options,
  }: {
    id: string;
    options?: Parameters<Page['goto']>[1];
  }) {
    await this.page.goto(Paths.processInstance(id), options);
  }

  async selectFlowNode(flowNodeName: string) {
    await this.diagram.getByText(flowNodeName).click();
  }

  async getNthTreeNodeTestId(n: number) {
    return this.page
      .getByTestId(/^tree-node-/)
      .nth(n)
      .getAttribute('data-testid');
  }
}
