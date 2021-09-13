/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';

import {currentInstanceStore} from 'modules/stores/currentInstance';
import {Header} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createMemoryHistory} from 'history';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {instancesStore} from 'modules/stores/instances';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';

// Header component fetches user information in the background, which is an async action and might complete after the tests finished.
// Tests also depend on the statistics fetch to be completed, in order to test the results that are rendered in the screen.
// So we have to use this function in all tests, in order to verify all the async actions are completed, when we want them to be completed.
const waitForComponentToLoad = async () => {
  expect(await screen.findByText('firstname lastname')).toBeInTheDocument();
};

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => (
    <ThemeProvider>
      <Router history={history}>{children}</Router>
    </ThemeProvider>
  );
  return Wrapper;
}

describe('Header', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(
          ctx.json({
            firstname: 'firstname',
            lastname: 'lastname',
          })
        )
      ),
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'first_instance_id',
            state: 'ACTIVE',
          })
        )
      )
    );
  });

  afterEach(() => {
    currentInstanceStore.reset();
    instancesStore.reset();
    clearStateLocally();
  });

  it('should render all header links', async () => {
    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/'],
        })
      ),
    });

    await waitForComponentToLoad();

    expect(screen.getByText('Operate')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Instances')).toBeInTheDocument();
  });

  it('should render user element', async () => {
    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/'],
        })
      ),
    });

    await waitForComponentToLoad();
    expect(screen.getByText('firstname lastname')).toBeInTheDocument();
  });

  it('should go to the correct pages when clicking on header links', async () => {
    storeStateLocally({
      filters: {
        active: true,
        incidents: true,
        completed: true,
      },
    });
    const MOCK_HISTORY = createMemoryHistory();
    render(<Header />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    await waitForComponentToLoad();

    userEvent.click(await screen.findByText('Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.click(await screen.findByText('Instances'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true'
    );

    userEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');
  });

  it('should preserve persistent params', async () => {
    storeStateLocally({
      filters: {
        active: true,
        incidents: true,
        completed: true,
      },
    });
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });
    render(<Header />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    await waitForComponentToLoad();

    userEvent.click(await screen.findByText('Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    userEvent.click(await screen.findByText('Instances'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&active=true&incidents=true&completed=true'
    );

    userEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });
});
