/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.RoleType;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRoleRequestDto {

  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
  private String id;

  private IdentityDto identity;
  private RoleType role;

  public CollectionRoleRequestDto(final IdentityDto identity, final RoleType role) {
    setIdentity(identity);
    this.role = role;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  public void setIdentity(final IdentityDto identity) {
    id = convertIdentityToRoleId(identity);
    this.identity = identity;
  }

  private String convertIdentityToRoleId(final IdentityDto identity) {
    return identity.getType() == null
        ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
        : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }

  public enum Fields {
    id,
    identity,
    role
  }
}
