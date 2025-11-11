package com.demo.testing.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to enable Azure Cosmos DB (NoSQL API) TestContainer for integration tests.
 * When applied to a test class, Cosmos NoSQL DB will be automatically started and configured.
 *
 * Note: Uses Testcontainers with Azure Cosmos Emulator Docker image
 */
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@interface EnableCosmosNoSqlTest { }

