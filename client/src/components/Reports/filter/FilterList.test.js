import React from 'react';

import FilterList from './FilterList';
import {getFlowNodeNames} from 'services';


import {mount} from 'enzyme';

jest.mock('components', () => {return {
  ActionItem: props => <button {...props}>{props.children}</button>
}});

jest.mock('services', () => {
  return {getFlowNodeNames: jest.fn()};
});

getFlowNodeNames.mockReturnValue({flowNode: 'flow Node hello'});

it('should render an unordered list', () => {
  const node = mount(<FilterList data={[]} />);

  expect(node.find('ul')).toBePresent();
});

it('should display a single filter entry for two related start dates', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [{
    type: 'date',
    data: {
      operator: '>=',
      value: startDate
    }
  }, {
    type: 'date',
    data: {
      operator: '<=',
      value: endDate
    }
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}/>);

  expect(node.find('li').length).toBe(1);
});

it('should display "and" between filter entries', () => {
  const data = ['Filter 1', 'Filter 2'];

  const node = mount(<FilterList data={data} />);

  expect(node).toIncludeText('and');
});

it('should remove both date filter parts for a date filter entry', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [{
    type: 'date',
    data: {
      operator: '>=',
      value: startDate
    }
  }, {
    type: 'date',
    data: {
      operator: '<=',
      value: endDate
    }
  }];
  const spy = jest.fn();

  const node = mount(<FilterList data={data} deleteFilter={spy} openEditFilterModal={jest.fn()}/>);

  node.find('button').simulate('click');

  expect(spy.mock.calls[0].length).toBe(2);
  expect(spy.mock.calls[0][0]).toBe(data[0]);
  expect(spy.mock.calls[0][1]).toBe(data[1]);
});

it('should display a simple variable filter', () => {
  const data = [{
    type: 'variable',
    data: {
      name: 'varName',
      operator: 'in',
      values: ['varValue']
    }
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}/>);

  expect(node).toIncludeText('varName is varValue');
});

it('should combine multiple variable values with or', () => {
  const data = [{
    type: 'variable',
    data: {
      name: 'varName',
      operator: 'in',
      values: ['varValue', 'varValue2']
    }
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}/>);

  expect(node).toIncludeText('varName is varValue or varValue2');
});

it('should combine multiple variable names with neither/nor for the not in operator', () => {
  const data = [{
    type: 'variable',
    data: {
      name: 'varName',
      operator: 'not in',
      values: ['varValue', 'varValue2']
    }
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}/>);

  expect(node).toIncludeText('varName is neither varValue nor varValue2');
});

it('should display a simple flow node filter', async () => {
  const data = [{
    type: 'executedFlowNodes',
    data: {
      operator: 'in',
      values: ['flowNode']
    }
  }];

  const node = mount(<FilterList id={'qwe'} data={data} openEditFilterModal={jest.fn()}/>);

  expect(node).toIncludeText('Executed Flow Node is flowNode');
});

it('should display flow node name instead of id after the names are loaded', async () => {
  const data = [{
    type: 'executedFlowNodes',
    data: {
      operator: 'in',
      values: ['flowNode']
    }
  }];

  const node = mount(<FilterList id={'qwe'} processDefinitionId={12} data={data} openEditFilterModal={jest.fn()}/>);
  await node.instance().loadFlowNodeNames();

  expect(node).toIncludeText('Executed Flow Node is flow Node hello');
});

it('should display a flow node filter with multiple selected nodes', () => {
  const data = [{
    type: 'executedFlowNodes',
    data: {
      operator: 'in',
      values: ['flowNode1', 'flowNode2']
    }
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}/>);

  expect(node).toIncludeText('Executed Flow Node is flowNode1 or flowNode2');
});

it('should display a rolling date filter', () => {
  const data = [{
    type: 'rollingDate',
    data: {
      value: 18,
      unit: 'hours'
    }
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}a/>);

  expect(node).toIncludeText('Start Date less than 18 hours ago');
});

it('should display a duration filter', () => {
  const data = [{
    type: 'processInstanceDuration',
    data: {
      operator: '<',
      value: 18,
      unit: 'hours'
    }
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}/>);

  expect(node).toIncludeText('Duration is less than 18 hours');
});

it('should display a running instances only filter', () => {
  const data = [{
    type: 'runningInstancesOnly',
    data: null
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}a/>);

  expect(node).toIncludeText('Running Process Instances Only');
});

it('should display a completed instances only filter', () => {
  const data = [{
    type: 'completedInstancesOnly',
    data: null
  }];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()}a/>);

  expect(node).toIncludeText('Completed Process Instances Only');
});
