/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityNameForm} from 'components';

import {updateProcess, loadProcess, getCleanedMappings} from './service';
import ProcessEditWithErrorHandling from './ProcessEdit';
import ProcessRenderer from './ProcessRenderer';
import EventTable from './EventTable';

const ProcessEdit = ProcessEditWithErrorHandling.WrappedComponent;

const props = {
  id: '1',
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

jest.mock('saveGuard', () => ({
  nowDirty: jest.fn(),
  nowPristine: jest.fn()
}));

jest.mock('./service', () => ({
  updateProcess: jest.fn(),
  getCleanedMappings: jest.fn(),
  createProcess: jest.fn(),
  loadProcess: jest.fn().mockReturnValue({
    name: 'Process Name',
    xml: 'Process XML',
    mappings: {},
    eventSources: []
  })
}));

it('should match snapshot', () => {
  const node = shallow(<ProcessEdit {...props} />);

  expect(node).toMatchSnapshot();
});

it('should load process by id', () => {
  shallow(<ProcessEdit {...props} />);

  expect(loadProcess).toHaveBeenCalledWith('1');
});

it('should initalize a new process', () => {
  loadProcess.mockClear();
  shallow(<ProcessEdit {...props} id="new" />);

  expect(loadProcess).not.toHaveBeenCalled();
});

it('should update the process', async () => {
  const saveSpy = jest.fn();
  const node = shallow(<ProcessEdit {...props} onSave={saveSpy} />);

  await node.find(EntityNameForm).prop('onSave')();

  expect(updateProcess).toHaveBeenCalledWith('1', 'Process Name', 'Process XML', {}, []);
  expect(saveSpy).toHaveBeenCalledWith('1');
});

it('should set a new mapping', () => {
  const node = shallow(<ProcessEdit {...props} />);
  node.setState({selectedNode: {id: 'a'}});

  node.find(EventTable).prop('onMappingChange')({eventName: '1'}, true);

  expect(node.find(EventTable).prop('mappings')).toEqual({a: {end: {eventName: '1'}, start: null}});
});

it('should edit a mapping', () => {
  loadProcess.mockReturnValueOnce({
    name: 'Process Name',
    xml: 'Process XML',
    mappings: {a: {end: {eventName: '1'}, start: null}}
  });
  const node = shallow(<ProcessEdit {...props} />);
  node.setState({selectedNode: {id: 'a'}});

  node.find(EventTable).prop('onMappingChange')({eventName: '1'}, true, 'start');

  expect(node.find(EventTable).prop('mappings')).toEqual({a: {start: {eventName: '1'}, end: null}});
});

it('should unset a mapping', () => {
  loadProcess.mockReturnValueOnce({
    name: 'Process Name',
    xml: 'Process XML',
    mappings: {a: {end: {eventName: '1'}, start: {eventName: '2'}}}
  });
  const node = shallow(<ProcessEdit {...props} />);
  node.setState({selectedNode: {id: 'a'}});

  node.find(EventTable).prop('onMappingChange')({eventName: '1'}, false);

  expect(node.find(EventTable).prop('mappings')).toEqual({a: {start: {eventName: '2'}, end: null}});

  node.find(EventTable).prop('onMappingChange')({eventName: '2'}, false);

  expect(node.find(EventTable).prop('mappings')).toEqual({});
});

it('should set new sources', async () => {
  const node = shallow(<ProcessEdit {...props} />);

  const newSources = [{processDefinitionKey: 'newSource'}];

  node.find(EventTable).prop('onSourcesChange')(newSources, true);

  expect(getCleanedMappings).toHaveBeenCalled();
  await node.update();
  expect(node.find(EventTable).prop('eventSources')).toEqual(newSources);
});

it('should update mappings when a node is removed', () => {
  const node = shallow(
    <ProcessEdit {...props} initialMappings={{a: {end: {eventName: '1'}, start: null}}} />
  );

  node.find(ProcessRenderer).prop('onChange')('new Xml', true);

  expect(getCleanedMappings).toHaveBeenCalled();
});

it('should select an element from the event table', () => {
  const endEvent = {eventName: '1', group: 'test', source: 'test'};

  loadProcess.mockReturnValueOnce({
    name: 'Process Name',
    xml: 'Process XML',
    mappings: {a: {end: endEvent, start: null}}
  });
  const node = shallow(<ProcessEdit {...props} />);

  node.instance().viewer = {
    get: el => {
      if (el === 'elementRegistry') {
        return [{id: 'a'}];
      }
      return {
        reset: jest.fn(),
        select: jest.fn(),
        viewbox: () => ({x: 5, y: 5, width: 5, height: 5})
      };
    }
  };

  node.find(EventTable).prop('onSelectEvent')(endEvent);
});
