package com.demo.testing.utils

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.net.InetSocketAddress

/**
 * Utility class for Azure Cosmos DB (Cassandra API) testing operations.
 * Provides methods to connect and interact with Cosmos DB using Cassandra driver.
 *
 * Note: Cosmos DB Cassandra API is compatible with Cassandra driver, with some limitations.
 */
class CosmosCassandraTestUtils {

    private static final Logger logger = LoggerFactory.getLogger(CosmosCassandraTestUtils.class)

    private final String contactPoint
    private final int port
    private final String username
    private final String password
    private CqlSession session

    CosmosCassandraTestUtils(String contactPoint, int port, String username = "", String password = "") {
        this.contactPoint = contactPoint
        this.port = port
        this.username = username
        this.password = password
    }

    /**
     * Create and return a CQL session to Cosmos DB (Cassandra API).
     *
     * @return CqlSession connected to Cosmos DB
     */
    CqlSession createSession() {
        if (session == null) {
            try {
                def builder = CqlSession.builder()
                        .addContactPoint(new InetSocketAddress(contactPoint, port))
                        .withLocalDatacenter("datacenter1")

                // Add authentication if provided
                if (username && password) {
                    builder.withAuthCredentials(username, password)
                    logger.info("Creating Cosmos DB session with authentication")
                } else {
                    logger.info("Creating Cosmos DB session without authentication")
                }

                session = builder.build()
                logger.info("✅ Cosmos DB (Cassandra API) session created: {}:{}", contactPoint, port)
            } catch (Exception e) {
                logger.error("❌ Failed to create Cosmos DB session", e)
                throw new RuntimeException("Failed to create Cosmos DB session", e)
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
     * Create a keyspace in Cosmos DB (if supported by your account).
     * Note: Cosmos DB has limitations on keyspace creation.
     *
     * @param keyspaceName Name of the keyspace
     */
    void createKeyspaceIfNotExists(String keyspaceName) {
        try {
            // Cosmos DB typically uses a simplified replication strategy
            String cql = """
                CREATE KEYSPACE IF NOT EXISTS ${keyspaceName}
                WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1}
            """
            executeMutation(cql)
            logger.info("✅ Keyspace '{}' created or already exists in Cosmos DB", keyspaceName)
        } catch (Exception e) {
            logger.warn("⚠️ Warning creating keyspace in Cosmos DB (may not be supported): {}", e.message)
            logger.info("Continuing without explicit keyspace creation...")
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
     * Close the Cosmos DB session.
     */
    void closeSession() {
        if (session != null) {
            try {
                session.close()
                logger.info("✅ Cosmos DB session closed")
            } catch (Exception e) {
                logger.warn("⚠️ Warning closing Cosmos DB session", e)
            }
        }
    }
}

