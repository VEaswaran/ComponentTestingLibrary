package com.demo.testing.utils

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.net.InetSocketAddress

/**
 * Utility class for Cassandra testing operations.
 * Provides methods to execute CQL queries, manage sessions, and verify data.
 */
class CassandraTestUtils {

    private static final Logger logger = LoggerFactory.getLogger(CassandraTestUtils.class)

    private final String contactPoint
    private final int port
    private CqlSession session

    CassandraTestUtils(String contactPoint, int port) {
        this.contactPoint = contactPoint
        this.port = port
    }

    /**
     * Create and return a CQL session to Cassandra.
     *
     * @return CqlSession connected to Cassandra
     */
    CqlSession createSession() {
        if (session == null) {
            try {
                session = CqlSession.builder()
                        .addContactPoint(new InetSocketAddress(contactPoint, port))
                        .withLocalDatacenter("datacenter1")
                        .build()
                logger.info("✅ Cassandra session created: {}:{}", contactPoint, port)
            } catch (Exception e) {
                logger.error("❌ Failed to create Cassandra session", e)
                throw new RuntimeException("Failed to create Cassandra session", e)
            }
        }
        return session
    }

    /**
     * Execute a CQL query and return the ResultSet.
     *
     * @param cql The CQL query to execute
     * @return ResultSet from the query
     */
    ResultSet executeQuery(String cql) {
        try {
            logger.info("Executing CQL: {}", cql)
            CqlSession sess = createSession()
            return sess.execute(cql)
        } catch (Exception e) {
            logger.error("❌ Failed to execute CQL query: {}", cql, e)
            throw new RuntimeException("Failed to execute CQL query", e)
        }
    }

    /**
     * Execute a CQL query and return all rows as a list.
     *
     * @param cql The CQL query to execute
     * @return List of Row objects
     */
    List<Row> executeQueryAndGetRows(String cql) {
        try {
            ResultSet resultSet = executeQuery(cql)
            return resultSet.all()
        } catch (Exception e) {
            logger.error("❌ Failed to execute query and get rows", e)
            throw new RuntimeException("Failed to execute query and get rows", e)
        }
    }

    /**
     * Execute a CQL mutation (INSERT, UPDATE, DELETE).
     *
     * @param cql The CQL mutation to execute
     */
    void executeMutation(String cql) {
        try {
            logger.info("Executing CQL mutation: {}", cql)
            createSession().execute(cql)
            logger.info("✅ CQL mutation executed successfully")
        } catch (Exception e) {
            logger.error("❌ Failed to execute CQL mutation: {}", cql, e)
            throw new RuntimeException("Failed to execute CQL mutation", e)
        }
    }

    /**
     * Create a keyspace if it doesn't exist.
     * Simple method name for convenient usage in tests.
     *
     * @param keyspaceName Name of the keyspace
     */
    void createKeyspace(String keyspaceName) {
        createKeyspaceIfNotExists(keyspaceName, 1)
    }

    /**
     * Create a keyspace if it doesn't exist.
     *
     * @param keyspaceName Name of the keyspace
     * @param replicationFactor Replication factor
     */
    void createKeyspaceIfNotExists(String keyspaceName, int replicationFactor = 1) {
        try {
            String cql = """
                CREATE KEYSPACE IF NOT EXISTS ${keyspaceName}
                WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': ${replicationFactor}}
            """
            executeMutation(cql)
            logger.info("✅ Keyspace '{}' created or already exists", keyspaceName)
        } catch (Exception e) {
            logger.error("❌ Failed to create keyspace: {}", keyspaceName, e)
            throw new RuntimeException("Failed to create keyspace", e)
        }
    }

    /**
     * Create a table in the keyspace.
     *
     * @param keyspaceName Keyspace name
     * @param tableName Table name
     * @param tableDefinition CQL table definition (without CREATE TABLE)
     */
    void createTable(String keyspaceName, String tableName, String tableDefinition) {
        try {
            String cql = "CREATE TABLE IF NOT EXISTS ${keyspaceName}.${tableName} ${tableDefinition}"
            executeMutation(cql)
            logger.info("✅ Table '{}.{}' created or already exists", keyspaceName, tableName)
        } catch (Exception e) {
            logger.error("❌ Failed to create table: {}.{}", keyspaceName, tableName, e)
            throw new RuntimeException("Failed to create table", e)
        }
    }

    /**
     * Close the Cassandra session.
     */
    void closeSession() {
        if (session != null) {
            try {
                session.close()
                logger.info("✅ Cassandra session closed")
            } catch (Exception e) {
                logger.warn("⚠️ Warning closing Cassandra session", e)
            }
        }
    }
}

