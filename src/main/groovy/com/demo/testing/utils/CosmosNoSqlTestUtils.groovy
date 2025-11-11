package com.demo.testing.utils

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import com.azure.cosmos.CosmosContainer
import com.azure.cosmos.CosmosDatabase
import com.azure.cosmos.models.*
import com.azure.cosmos.util.CosmosPagedIterable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Utility class for Azure Cosmos DB (NoSQL API) testing operations.
 * Provides methods to connect and interact with Cosmos NoSQL DB using the Azure SDK.
 *
 * Features:
 * - Creates and manages Cosmos DB client connections
 * - CRUD operations on documents
 * - Query operations using SQL
 * - Container and database management
 * - Automatic resource cleanup
 *
 * Note: This class works with both Cosmos Emulator and actual Azure Cosmos DB
 */
class CosmosNoSqlTestUtils {

    private static final Logger logger = LoggerFactory.getLogger(CosmosNoSqlTestUtils.class)

    static {
        // Disable SSL certificate validation for Cosmos DB Emulator
        try {
            logger.debug("Disabling SSL certificate validation for Cosmos Emulator...")

            def trustAllCerts = [
                new X509TrustManager() {
                    X509Certificate[] getAcceptedIssuers() { null }
                    void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            ] as TrustManager[]

            def sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom())
            SSLContext.setDefault(sslContext)

            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { hostname, session -> true }

            logger.debug("✅ SSL certificate validation disabled")
        } catch (Exception e) {
            logger.warn("⚠️ Warning: Could not disable SSL certificate validation: {}", e.message)
        }
    }

    private final String endpoint
    private final String key
    private CosmosClient cosmosClient
    private CosmosDatabase database
    private CosmosContainer container

    /**
     * Constructor for CosmosNoSqlTestUtils
     *
     * @param endpoint The Cosmos DB endpoint URL (e.g., https://localhost:8081)
     * @param key The primary key for authentication
     */
    CosmosNoSqlTestUtils(String endpoint, String key) {
        this.endpoint = endpoint
        this.key = key
    }

    /**
     * Create and return a Cosmos DB client.
     *
     * @return CosmosClient connected to Cosmos DB
     */
    CosmosClient createClient() {
        if (cosmosClient == null) {
            try {
                // Create SSL context that trusts self-signed certificates (for Emulator)
                def trustAllCerts = [
                    new X509TrustManager() {
                        X509Certificate[] getAcceptedIssuers() { null }
                        void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                ] as TrustManager[]

                def sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom())

                cosmosClient = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .key(key)
                        .consistencyLevel(ConsistencyLevel.SESSION)
                        .sslContext(sslContext)
                        .buildClient()
                logger.info("✅ Cosmos NoSQL DB client created: {}", endpoint)
            } catch (Exception e) {
                logger.error("❌ Failed to create Cosmos NoSQL DB client", e)
                throw new RuntimeException("Failed to create Cosmos NoSQL DB client", e)
            }
        }
        return cosmosClient
    }

    /**
     * Get or create a database.
     *
     * @param databaseId The database ID
     * @param throughput The throughput (RU/s) for the database
     * @return CosmosDatabase
     */
    CosmosDatabase getOrCreateDatabase(String databaseId, int throughput = 400) {
        try {
            if (database == null || !database.getId().equals(databaseId)) {
                logger.info("Creating or getting database: {}", databaseId)
                def client = createClient()
                database = client.createDatabaseIfNotExists(databaseId,
                    ThroughputProperties.createManualThroughput(throughput)
                ).getDatabase(databaseId)
                logger.info("✅ Database '{}' ready", databaseId)
            }
            return database
        } catch (Exception e) {
            logger.error("❌ Failed to create/get database: {}", databaseId, e)
            throw new RuntimeException("Failed to create/get database", e)
        }
    }

    /**
     * Get or create a container.
     *
     * @param databaseId The database ID
     * @param containerId The container ID
     * @param partitionKeyPath The partition key path (e.g., "/id")
     * @return CosmosContainer
     */
    CosmosContainer getOrCreateContainer(String databaseId, String containerId, String partitionKeyPath = "/id") {
        try {
            if (container == null || !container.getId().equals(containerId)) {
                logger.info("Creating or getting container: {} in database: {}", containerId, databaseId)
                def db = getOrCreateDatabase(databaseId)

                def containerProperties = new CosmosContainerProperties(
                    containerId,
                    partitionKeyPath
                )

                container = db.createContainerIfNotExists(containerProperties).getContainer(containerId)
                logger.info("✅ Container '{}' ready with partition key '{}'", containerId, partitionKeyPath)
            }
            return container
        } catch (Exception e) {
            logger.error("❌ Failed to create/get container: {}", containerId, e)
            throw new RuntimeException("Failed to create/get container", e)
        }
    }

    /**
     * Insert a document into a container.
     *
     * @param databaseId Database ID
     * @param containerId Container ID
     * @param document The document to insert (Map or POJO)
     * @param partitionKeyValue The partition key value
     * @return The inserted document response
     */
    Object insertDocument(String databaseId, String containerId, Object document, String partitionKeyValue) {
        try {
            logger.info("Inserting document into {}.{}", databaseId, containerId)
            def container = getOrCreateContainer(databaseId, containerId)
            def response = container.createItem(document)
            logger.info("✅ Document inserted successfully")
            return response
        } catch (Exception e) {
            logger.error("❌ Failed to insert document", e)
            throw new RuntimeException("Failed to insert document", e)
        }
    }

    /**
     * Read a document from a container.
     *
     * @param databaseId Database ID
     * @param containerId Container ID
     * @param documentId The document ID
     * @param partitionKeyValue The partition key value
     * @return The document
     */
    Object readDocument(String databaseId, String containerId, String documentId, String partitionKeyValue) {
        try {
            logger.info("Reading document {} from {}.{}", documentId, databaseId, containerId)
            def container = getOrCreateContainer(databaseId, containerId)
            def response = container.readItem(documentId, new PartitionKey(partitionKeyValue), Object.class)
            logger.info("✅ Document read successfully")
            return response.item
        } catch (Exception e) {
            logger.error("❌ Failed to read document: {}", documentId, e)
            throw new RuntimeException("Failed to read document", e)
        }
    }

    /**
     * Update a document in a container.
     *
     * @param databaseId Database ID
     * @param containerId Container ID
     * @param documentId The document ID
     * @param updatedDocument The updated document
     * @param partitionKeyValue The partition key value
     * @return The updated document response
     */
    Object updateDocument(String databaseId, String containerId, String documentId, Object updatedDocument, String partitionKeyValue) {
        try {
            logger.info("Updating document {} in {}.{}", documentId, databaseId, containerId)
            def container = getOrCreateContainer(databaseId, containerId)
            def options = new CosmosItemRequestOptions()
            def response = container.upsertItem(updatedDocument, options)
            logger.info("✅ Document updated successfully")
            return response
        } catch (Exception e) {
            logger.error("❌ Failed to update document: {}", documentId, e)
            throw new RuntimeException("Failed to update document", e)
        }
    }

    /**
     * Delete a document from a container.
     *
     * @param databaseId Database ID
     * @param containerId Container ID
     * @param documentId The document ID
     * @param partitionKeyValue The partition key value
     */
    void deleteDocument(String databaseId, String containerId, String documentId, String partitionKeyValue) {
        try {
            logger.info("Deleting document {} from {}.{}", documentId, databaseId, containerId)
            def container = getOrCreateContainer(databaseId, containerId)
            container.deleteItem(documentId, new PartitionKey(partitionKeyValue))
            logger.info("✅ Document deleted successfully")
        } catch (Exception e) {
            logger.error("❌ Failed to delete document: {}", documentId, e)
            throw new RuntimeException("Failed to delete document", e)
        }
    }

    /**
     * Query documents from a container using SQL.
     *
     * @param databaseId Database ID
     * @param containerId Container ID
     * @param sqlQuery The SQL query
     * @return List of results
     */
    List<Object> queryDocuments(String databaseId, String containerId, String sqlQuery) {
        try {
            logger.info("Executing query: {}", sqlQuery)
            def container = getOrCreateContainer(databaseId, containerId)
            def queryOptions = new CosmosQueryRequestOptions()
            CosmosPagedIterable<Object> results = container.queryItems(sqlQuery, queryOptions, Object.class)
            def resultList = results.stream().collect(java.util.stream.Collectors.toList())
            logger.info("✅ Query executed successfully, returned {} documents", resultList.size())
            return resultList
        } catch (Exception e) {
            logger.error("❌ Failed to execute query: {}", sqlQuery, e)
            throw new RuntimeException("Failed to execute query", e)
        }
    }

    /**
     * Delete a container.
     *
     * @param databaseId Database ID
     * @param containerId Container ID
     */
    void deleteContainer(String databaseId, String containerId) {
        try {
            logger.info("Deleting container: {}", containerId)
            def db = getOrCreateDatabase(databaseId)
            db.getContainer(containerId).delete()
            logger.info("✅ Container deleted successfully")
            container = null
        } catch (Exception e) {
            logger.error("❌ Failed to delete container: {}", containerId, e)
            throw new RuntimeException("Failed to delete container", e)
        }
    }

    /**
     * Delete a database.
     *
     * @param databaseId Database ID
     */
    void deleteDatabase(String databaseId) {
        try {
            logger.info("Deleting database: {}", databaseId)
            createClient().getDatabase(databaseId).delete()
            logger.info("✅ Database deleted successfully")
            database = null
        } catch (Exception e) {
            logger.error("❌ Failed to delete database: {}", databaseId, e)
            throw new RuntimeException("Failed to delete database", e)
        }
    }

    /**
     * Close the Cosmos DB client and cleanup resources.
     */
    void closeClient() {
        if (cosmosClient != null) {
            try {
                cosmosClient.close()
                logger.info("✅ Cosmos NoSQL DB client closed")
            } catch (Exception e) {
                logger.warn("⚠️ Warning closing Cosmos NoSQL DB client", e)
            }
        }
    }
}

