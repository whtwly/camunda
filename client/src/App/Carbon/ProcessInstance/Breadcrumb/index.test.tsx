/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {Breadcrumb} from './index';
import {createInstance} from 'modules/testUtils';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';

const createWrapper = (initialPath: string = Paths.processInstance('123')) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path={Paths.processInstance()} element={children} />
      </Routes>
      <LocationLog />
    </MemoryRouter>
  );

  return Wrapper;
};

const processInstance = {
  ...createInstance(),
  id: '123',
  processName: 'Base instance name',
  callHierarchy: [
    {
      instanceId: '546546543276',
      processDefinitionName: 'Parent Process Name',
    },
    {
      instanceId: '968765314354',
      processDefinitionName: '1st level Child Process Name',
    },
    {
      instanceId: '2251799813685447',
      processDefinitionName: '2nd level Child Process Name',
    },
  ],
};
describe('User', () => {
  it('should render breadcrumb', async () => {
    render(<Breadcrumb processInstance={processInstance} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText('Parent Process Name')).toBeInTheDocument();
    expect(
      screen.getByText('1st level Child Process Name'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('2nd level Child Process Name'),
    ).toBeInTheDocument();
    expect(screen.getByText('Base instance name')).toBeInTheDocument();
  });

  it('should navigate to instance detail on click', async () => {
    const {user} = render(<Breadcrumb processInstance={processInstance} />, {
      wrapper: createWrapper(Paths.processInstance('123')),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/123$/,
    );

    await user.click(
      screen.getByRole('link', {
        description: /View Process Parent Process Name - Instance 546546543276/,
      }),
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/546546543276$/,
    );

    await user.click(
      screen.getByRole('link', {
        description:
          /View Process 1st level Child Process Name - Instance 968765314354/,
      }),
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/968765314354$/,
    );

    await user.click(
      screen.getByRole('link', {
        description:
          /View Process 2nd level Child Process Name - Instance 2251799813685447/,
      }),
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/2251799813685447$/,
    );
  });
});
