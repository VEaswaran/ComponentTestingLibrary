package com.demo.testing.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to enable Azure Cosmos DB (Cassandra API) TestContainer for integration tests.
 * When applied to a test class, Cosmos Cassandra will be automatically started and configured.
 */
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@interface EnableCosmosCassandraTest { }

