/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.query;

import static io.zeebe.tasklist.util.CollectionUtil.map;

import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.zeebe.tasklist.webapp.es.TaskReaderWriter;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.TaskQueryDTO;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class TaskQueryResolver implements GraphQLQueryResolver {

  @Autowired private TaskReaderWriter taskReaderWriter;

  public List<TaskDTO> tasks(TaskQueryDTO query, DataFetchingEnvironment dataFetchingEnvironment) {
    return taskReaderWriter.getTasks(query, getFieldNames(dataFetchingEnvironment));
  }

  public TaskDTO task(String id, DataFetchingEnvironment dataFetchingEnvironment) {
    return taskReaderWriter.getTask(id, getFieldNames(dataFetchingEnvironment));
  }

  private List<String> getFieldNames(DataFetchingEnvironment dataFetchingEnvironment) {
    return map(dataFetchingEnvironment.getSelectionSet().getFields(), SelectedField::getName);
  }
}
