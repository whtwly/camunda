/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ProcessInstance, Task} from 'modules/types';
import {RequestHandler, rest} from 'msw';
import * as processInstancesMocks from 'modules/mock-schema/mocks/process-instances';

const handlers: RequestHandler[] = [
  rest.post('/internal/users/:userId/process-instances', (_, res, ctx) => {
    return res(
      ctx.json<ProcessInstance[]>(processInstancesMocks.processInstances),
    );
  }),
  rest.get('/v1/internal/users/current', async (req, res, ctx) => {
    const originalResponse = await ctx.fetch(req);
    const originalBody = await originalResponse.json();

    return res(
      ctx.json({
        ...originalBody,
        tenants: [
          {
            id: 'tenantA',
            name: 'Tenant A',
          },
          {
            id: 'tenantB',
            name: 'Tenant B',
          },
        ],
      }),
    );
  }),
  rest.post('/v1/tasks/search', async (req, res, ctx) => {
    const originalResponse = await ctx.fetch(req);
    const originalBody = (await originalResponse.json()) as Task[];

    return res(
      ctx.json(
        originalBody.map((task: Task, index) => ({
          ...task,
          tenantId: index % 2 === 0 ? 'tenantA' : null,
        })),
      ),
    );
  }),
  rest.get('/v1/tasks/:taskId', async (req, res, ctx) => {
    const originalResponse = await ctx.fetch(req);
    const originalBody = await originalResponse.json();

    return res(
      ctx.json({
        ...originalBody,
        tenantId: Math.random() > 0.5 ? 'tenantA' : null,
      }),
    );
  }),
];

export {handlers};
