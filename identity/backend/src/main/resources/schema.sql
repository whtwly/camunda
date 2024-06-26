/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

-- Ref: https://docs.spring.io/spring-security/reference/servlet/appendix/database-schema.html#_user_schema
CREATE TABLE IF NOT EXISTS users (
    id bigint generated by default as identity(start with 1) primary key,
    username varchar(50) not null unique,
    password varchar not null,
    enabled boolean not null
);

-- authorities == roles
CREATE TABLE IF NOT EXISTS authorities (
    username varchar not null,
    authority varchar not null,
    constraint fk_authorities_users foreign key(username) references users(username)
);
CREATE UNIQUE INDEX IF NOT EXISTS ix_auth_username on authorities (username,authority);

CREATE TABLE IF NOT EXISTS groups (
    id bigint generated by default as identity(start with 1) primary key,
    group_name varchar(50) not null,
    organization_id varchar(250) null
);

-- group roles
CREATE TABLE IF NOT EXISTS group_authorities (
    group_id bigint not null,
    authority varchar(50) not null,
    constraint fk_group_authorities_group foreign key(group_id) references groups(id)
);

CREATE TABLE IF NOT EXISTS group_members (
    id bigint generated by default as identity(start with 1) primary key,
    username varchar(50) not null,
    group_id bigint not null,
    constraint fk_group_members_group foreign key(group_id) references groups(id),
    constraint uk_group_members_group_username unique(group_id , username)
);

-- authority == role
CREATE TABLE IF NOT EXISTS roles(
    authority varchar(50) not null primary key,
    description varchar(500)
);

CREATE TABLE IF NOT EXISTS role_permissions(
    role_authority varchar(50) not null,
    permission varchar(50) not null,
    constraint unique_rp unique(role_authority, permission)
);


CREATE TABLE IF NOT EXISTS native_users(
                                           id bigint primary key,
                                           name varchar(500)
                                           email varchar(100) unique,
    name varchar,
    constraint fk_native_users_users foreign key(id) references users(id) ON DELETE CASCADE);

CREATE TABLE IF NOT EXISTS mapped_users(
                                           id bigint primary key,
                                           email varchar(100) unique,
    name varchar,
    constraint fk_native_users_users foreign key(id) references users(id) ON DELETE CASCADE);
