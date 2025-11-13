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
import javax.net.ssl.HttpsURLConnection
import java.security.cert.X509Certificate
import java.security.SecureRandom
import java.util.stream.Collectors

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
            sslContext.init(null, trustAllCerts, new SecureRandom())
            SSLContext.setDefault(sslContext)

            HttpsURLConnection.setDefaultHostnameVerifier { hostname, session -> true }

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

        // Log initialization
        logger.info("🔧 CosmosNoSqlTestUtils initialized")
        logger.info("   Endpoint: {}", endpoint)
        logger.info("   Key: {}...", key?.take(10) ?: "NULL")
    }

    /**
     * Create and return a Cosmos DB client.
     *
     * @return CosmosClient connected to Cosmos DB
     */
    CosmosClient createClient() {
        if (cosmosClient == null) {
            try {
                logger.info("========================================")
                logger.info("🔗 Creating Cosmos NoSQL DB client")
                logger.info("========================================")
                logger.info("📍 Endpoint: {}", endpoint)
                logger.info("🔑 Key: {}...", key?.take(10) ?: "NULL")

                // Validate endpoint
                if (endpoint == null || endpoint.isEmpty()) {
                    throw new IllegalArgumentException("❌ Endpoint is NULL or empty")
                }
                if (!endpoint.startsWith("https://")) {
                    throw new IllegalArgumentException("❌ Endpoint must start with https:// - got: ${endpoint}")
                }

                // Validate key
                if (key == null || key.isEmpty()) {
                    throw new IllegalArgumentException("❌ Key is NULL or empty")
                }

                logger.info("✅ Endpoint and key validation passed")

                // Create SSL context that trusts self-signed certificates (for Emulator)
                logger.info("🔒 Setting up SSL context for self-signed certificates...")
                def trustAllCerts = [
                    new X509TrustManager() {
                        X509Certificate[] getAcceptedIssuers() { null }
                        void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                ] as TrustManager[]

                def sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, trustAllCerts, new SecureRandom())
                logger.info("✅ SSL context configured")

                logger.info("🔨 Building CosmosClient...")
                cosmosClient = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .key(key)
                        .consistencyLevel(ConsistencyLevel.SESSION)
                        .sslContext(sslContext)
                        .gatewayMode()  // Use gateway mode for more stable emulator connections
                        .buildClient()

                logger.info("✅ CosmosClient built successfully")

                // Test the connection by reading account properties
                logger.info("🔍 Testing connection by reading account properties...")
                def accountProps = cosmosClient.readAccountProperties()
                logger.info("✅ Connection test successful!")
                logger.info("   Database Regions: {}", accountProps.readableLocations)

                logger.info("========================================")
                logger.info("✅ Cosmos NoSQL DB client created: {}", endpoint)
                logger.info("========================================")

            } catch (IllegalArgumentException iae) {
                logger.error("❌ Configuration Error: {}", iae.message)
                logger.error("   Please check your endpoint and key configuration")
                throw iae
            } catch (java.net.UnknownHostException e) {
                logger.error("❌ DNS Resolution Error: {}", e.message)
                logger.error("   Cannot resolve hostname from endpoint: {}", endpoint)
                logger.error("   Check if the emulator is running and the endpoint is correct")
                throw new RuntimeException("Failed to resolve endpoint hostname", e)
            } catch (java.net.ConnectException e) {
                logger.error("❌ Connection Refused: {}", e.message)
                logger.error("   Cannot connect to {}", endpoint)
                logger.error("   Possible causes:")
                logger.error("   - Cosmos Emulator container is not running")
                logger.error("   - Port 8081 is not exposed or mapped")
                logger.error("   - Firewall is blocking the connection")
                logger.error("   - Docker network issue")
                throw new RuntimeException("Cannot connect to Cosmos Emulator at ${endpoint}", e)
            } catch (javax.net.ssl.SSLException e) {
                logger.error("❌ SSL Error: {}", e.message)
                logger.error("   SSL certificate validation failed")
                logger.error("   This should have been disabled for the emulator")
                throw new RuntimeException("SSL certificate error - ensure SSL validation is disabled", e)
            } catch (java.util.concurrent.TimeoutException e) {
                logger.error("❌ Connection Timeout: {}", e.message)
                logger.error("   The emulator is not responding within the timeout period")
                logger.error("   Check if the emulator needs more time to start")
                throw new RuntimeException("Connection timeout - emulator may still be initializing", e)
            } catch (Exception e) {
                logger.error("❌ Failed to create Cosmos NoSQL DB client")
                logger.error("   Error Type: {}", e.class.name)
                logger.error("   Error Message: {}", e.message)
                logger.error("   Endpoint: {}", endpoint)
                logger.error("   Stack trace: ", e)

                // Print diagnostic info
                logger.error("📋 DIAGNOSTIC INFORMATION:")
                logger.error("   - Java Version: {}", System.getProperty("java.version"))
                logger.error("   - OS: {}", System.getProperty("os.name"))

                // Try to test if port is reachable
                try {
                    def url = new URL(endpoint)
                    def host = url.host
                    def port = url.port == -1 ? 8081 : url.port
                    logger.error("   - Parsed Host: {}", host)
                    logger.error("   - Parsed Port: {}", port)

                    def socket = new Socket()
                    socket.connect(new java.net.InetSocketAddress(host, port), 5000)
                    socket.close()
                    logger.error("   - Socket Connection: SUCCESS (port is open)")
                } catch (Exception socketEx) {
                    logger.error("   - Socket Connection: FAILED - {}", socketEx.message)
                }

                throw new RuntimeException("Failed to create Cosmos NoSQL DB client: ${e.message}", e)
            }
        }
        return cosmosClient
    }

    /**
     * Test connectivity to the Cosmos DB endpoint
     * This is useful for diagnosing connection issues before attempting to create a client
     */
    void testConnectivity() {
        logger.info("========================================")
        logger.info("🔍 Testing connectivity to: {}", endpoint)
        logger.info("========================================")

        try {
            def url = new URL(endpoint)
            def host = url.host
            def port = url.port == -1 ? 8081 : url.port

            logger.info("📍 Parsed Host: {}", host)
            logger.info("📍 Parsed Port: {}", port)

            // Test 1: DNS Resolution
            logger.info("🔍 Test 1: DNS Resolution...")
            try {
                def inetAddr = java.net.InetAddress.getByName(host)
                logger.info("   ✅ DNS resolved {} to {}", host, inetAddr.hostAddress)
            } catch (Exception dnsEx) {
                logger.error("   ❌ DNS FAILED: {}", dnsEx.message)
                logger.error("      Cannot resolve hostname '{}'", host)
                logger.error("      This is critical - check if emulator host/IP is correct")
                throw dnsEx
            }

            // Test 2: Socket Connection (TCP)
            logger.info("🔍 Test 2: Socket Connection (TCP)...")
            def socket = null
            try {
                socket = new Socket()
                socket.connect(new java.net.InetSocketAddress(host, port), 10000)
                logger.info("   ✅ Socket connection SUCCESS to {}:{}", host, port)
            } catch (Exception socketEx) {
                logger.error("   ❌ Socket connection FAILED: {}", socketEx.message)
                logger.error("      Cannot establish TCP connection to {}:{}", host, port)
                logger.error("      Possible causes:")
                logger.error("      - Emulator is not running")
                logger.error("      - Port {} is not open", port)
                logger.error("      - Firewall is blocking the connection")
                logger.error("      - Wrong host/IP address")
                throw socketEx
            } finally {
                if (socket != null) {
                    try { socket.close() } catch (Exception ignored) {}
                }
            }

            // Test 3: HTTPS Connection
            logger.info("🔍 Test 3: HTTPS Connection...")
            try {
                def conn = (javax.net.ssl.HttpsURLConnection) url.openConnection()
                conn.setConnectTimeout(10000)
                conn.setReadTimeout(10000)
                conn.setRequestMethod("GET")

                def responseCode = conn.getResponseCode()
                logger.info("   ✅ HTTPS connection SUCCESS - Response Code: {}", responseCode)
            } catch (Exception httpsEx) {
                logger.warn("   ⚠️ HTTPS test warning: {}", httpsEx.message)
                logger.warn("      This may be normal if the endpoint requires authentication")
                // Don't throw - some endpoints may require auth
            }

            logger.info("========================================")
            logger.info("✅ Connectivity tests PASSED")
            logger.info("========================================")

        } catch (Exception e) {
            logger.error("========================================")
            logger.error("❌ Connectivity tests FAILED")
            logger.error("========================================")
            logger.error("Error: {}", e.message)
            throw new RuntimeException("Cannot connect to endpoint: ${endpoint}", e)
        }
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

                logger.info("📡 Attempting to create/get database '{}'...", databaseId)
                client.createDatabaseIfNotExists(databaseId,
                    ThroughputProperties.createManualThroughput(throughput)
                )
                database = client.getDatabase(databaseId)
                logger.info("✅ Database '{}' ready", databaseId)
            }
            return database
        } catch (Exception e) {
            logger.error("❌ Failed to create/get database: {}", databaseId)
            logger.error("   Error: {}", e.class.name)
            logger.error("   Message: {}", e.message)
            logger.error("   This may indicate the client is not properly connected to the emulator")

            // Try to provide helpful diagnostics
            if (e.message?.contains("timed out")) {
                logger.error("   → Timeout: Emulator may not be responding")
                logger.error("   → Try waiting longer for emulator to initialize")
            } else if (e.message?.contains("Connection refused")) {
                logger.error("   → Connection refused: Port may not be open")
                logger.error("   → Check if emulator is running on the expected port")
            } else if (e.message?.contains("Unauthorized")) {
                logger.error("   → Unauthorized: Check your endpoint and key")
            }

            throw new RuntimeException("Failed to create/get database: ${e.message}", e)
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

                logger.info("📡 Attempting to create/get container '{}' with partition key '{}'...", containerId, partitionKeyPath)
                def containerProperties = new CosmosContainerProperties(
                    containerId,
                    partitionKeyPath
                )

                container = db.createContainerIfNotExists(containerProperties).getContainer(containerId)
                logger.info("✅ Container '{}' ready with partition key '{}'", containerId, partitionKeyPath)
            }
            return container
        } catch (Exception e) {
            logger.error("❌ Failed to create/get container: {}", containerId)
            logger.error("   Database: {}", databaseId)
            logger.error("   Error Type: {}", e.class.name)
            logger.error("   Error Message: {}", e.message)

            if (e.message?.contains("Forbidden")) {
                logger.error("   → Forbidden: Check that the key is valid and has sufficient permissions")
            } else if (e.message?.contains("timed out")) {
                logger.error("   → Timeout: The operation took too long, emulator may be slow")
            } else if (e.message?.contains("NotFound")) {
                logger.error("   → Database not found: Try creating the database first")
            }

            throw new RuntimeException("Failed to create/get container: ${e.message}", e)
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
            def requestOptions = new CosmosItemRequestOptions()
            container.deleteItem(documentId, requestOptions)
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
            def resultList = results.stream().collect(Collectors.toList())
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

