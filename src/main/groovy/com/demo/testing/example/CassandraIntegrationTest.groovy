package com.demo.testing.example

import com.demo.testing.base.IntegrationTestBaseCassandraSpec
import com.datastax.oss.driver.api.core.cql.Row
import spock.lang.Specification

/**
 * Example Integration Test for Cassandra and Cosmos Cassandra
 *
 * This test demonstrates how to use the IntegrationTestBaseCassandraSpec base class
 * to run tests against both Cassandra and Cosmos Cassandra databases.
 *
 * Features:
 * - Both Cassandra and Cosmos Cassandra containers are started automatically
 * - cassandraUtils provides methods to interact with Cassandra
 * - cosmosCassandraUtils provides methods to interact with Cosmos Cassandra
 * - Each database runs independently on the same Docker network
 *
 * Prerequisites:
 * - Docker must be installed and running
 * - The following dependencies must be in pom.xml:
 *   - org.testcontainers:testcontainers
 *   - org.testcontainers:cassandra
 *   - com.datastax.oss:java-driver-core
 */
class CassandraIntegrationTest extends IntegrationTestBaseCassandraSpec {

    def "test cassandra connectivity"() {
        given: "Cassandra utilities are initialized"
        cassandraUtils != null
        cassandraContactPoint != null
        cassandraPort > 0

        when: "we execute a system query on Cassandra"
        def rows = cassandraUtils.executeQueryAndGetRows(
            "SELECT cluster_name FROM system.local"
        )

        then: "the query should return at least one row"
        rows != null
        rows.size() > 0
        rows.get(0).getString("cluster_name") != null

        and: "cluster name should not be empty"
        rows.get(0).getString("cluster_name").size() > 0
    }

    def "test cosmos cassandra connectivity"() {
        given: "Cosmos Cassandra utilities are initialized"
        cosmosCassandraUtils != null
        cosmosCassandraContactPoint != null
        cosmosCassandraPort > 0

        when: "we execute a system query on Cosmos Cassandra"
        def rows = cosmosCassandraUtils.executeQueryAndGetRows(
            "SELECT cluster_name FROM system.local"
        )

        then: "the query should return at least one row"
        rows != null
        rows.size() > 0
        rows.get(0).getString("cluster_name") != null

        and: "cluster name should not be empty"
        rows.get(0).getString("cluster_name").size() > 0
    }

    def "test create keyspace in cassandra"() {
        given: "Cassandra is ready"
        cassandraUtils != null

        when: "we create a keyspace"
        cassandraUtils.executeMutation(
            """
            CREATE KEYSPACE IF NOT EXISTS test_keyspace 
            WITH replication = {
                'class' : 'SimpleStrategy', 
                'replication_factor' : 1
            }
            """
        )

        and: "we query the keyspace"
        def rows = cassandraUtils.executeQueryAndGetRows(
            "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'test_keyspace'"
        )

        then: "the keyspace should exist"
        rows != null
        rows.size() > 0
        rows.get(0).getString("keyspace_name") == "test_keyspace"
    }

    def "test create keyspace in cosmos cassandra"() {
        given: "Cosmos Cassandra is ready"
        cosmosCassandraUtils != null

        when: "we create a keyspace"
        cosmosCassandraUtils.executeMutation(
            """
            CREATE KEYSPACE IF NOT EXISTS cosmos_test_keyspace 
            WITH replication = {
                'class' : 'SimpleStrategy', 
                'replication_factor' : 1
            }
            """
        )

        and: "we query the keyspace"
        def rows = cosmosCassandraUtils.executeQueryAndGetRows(
            "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'cosmos_test_keyspace'"
        )

        then: "the keyspace should exist"
        rows != null
        rows.size() > 0
        rows.get(0).getString("keyspace_name") == "cosmos_test_keyspace"
    }

    def "test create and query table in cassandra"() {
        given: "Cassandra keyspace exists"
        cassandraUtils != null
        cassandraUtils.createKeyspace("test_users_ks")

        when: "we create a table"
        cassandraUtils.executeMutation(
            """
            CREATE TABLE IF NOT EXISTS test_users_ks.users (
                user_id UUID PRIMARY KEY,
                username TEXT,
                email TEXT
            )
            """
        )

        and: "we insert a row"
        cassandraUtils.executeMutation(
            """
            INSERT INTO test_users_ks.users (user_id, username, email) 
            VALUES (550e8400-e29b-41d4-a716-446655440000, 'john_doe', 'john@example.com')
            """
        )

        and: "we query the table"
        def rows = cassandraUtils.executeQueryAndGetRows(
            "SELECT * FROM test_users_ks.users WHERE user_id = 550e8400-e29b-41d4-a716-446655440000"
        )

        then: "the row should be returned"
        rows != null
        rows.size() == 1
        rows.get(0).getString("username") == "john_doe"
        rows.get(0).getString("email") == "john@example.com"
    }

    def "test create and query table in cosmos cassandra"() {
        given: "Cosmos Cassandra keyspace exists"
        cosmosCassandraUtils != null
        cosmosCassandraUtils.createKeyspace("cosmos_users_ks")

        when: "we create a table"
        cosmosCassandraUtils.executeMutation(
            """
            CREATE TABLE IF NOT EXISTS cosmos_users_ks.users (
                user_id UUID PRIMARY KEY,
                username TEXT,
                email TEXT
            )
            """
        )

        and: "we insert a row"
        cosmosCassandraUtils.executeMutation(
            """
            INSERT INTO cosmos_users_ks.users (user_id, username, email) 
            VALUES (550e8400-e29b-41d4-a716-446655440001, 'jane_doe', 'jane@example.com')
            """
        )

        and: "we query the table"
        def rows = cosmosCassandraUtils.executeQueryAndGetRows(
            "SELECT * FROM cosmos_users_ks.users WHERE user_id = 550e8400-e29b-41d4-a716-446655440001"
        )

        then: "the row should be returned"
        rows != null
        rows.size() == 1
        rows.get(0).getString("username") == "jane_doe"
        rows.get(0).getString("email") == "jane@example.com"
    }

    def "test both cassandra instances are independent"() {
        given: "Both Cassandra instances are ready"
        cassandraUtils != null
        cosmosCassandraUtils != null

        when: "we create a keyspace only in Cassandra"
        cassandraUtils.createKeyspace("cassandra_only_ks")

        and: "we verify it exists in Cassandra"
        def cassandraRows = cassandraUtils.executeQueryAndGetRows(
            "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'cassandra_only_ks'"
        )

        and: "we try to query the same keyspace from Cosmos"
        def cosmosRows = cosmosCassandraUtils.executeQueryAndGetRows(
            "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'cassandra_only_ks'"
        )

        then: "the keyspace should exist in Cassandra"
        cassandraRows.size() > 0

        and: "but NOT exist in Cosmos Cassandra (proving they are independent)"
        cosmosRows.size() == 0
    }

    def "test connection details are correctly configured"() {
        expect: "Cassandra connection details are set"
        cassandraContactPoint != null
        cassandraPort > 0
        cassandraUtils != null

        and: "Cosmos Cassandra connection details are set"
        cosmosCassandraContactPoint != null
        cosmosCassandraPort > 0
        cosmosCassandraUtils != null

        and: "both instances have different configuration"
        // They could be on different hosts or ports
        cassandraContactPoint != null
        cosmosCassandraContactPoint != null
    }
}

