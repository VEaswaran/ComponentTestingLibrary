package com.demo.testing.base

import com.demo.testing.utils.CassandraTestUtils
import com.demo.testing.utils.CosmosCassandraTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification
import java.time.Duration
import java.net.Socket
import java.net.InetSocketAddress

/**
 * Base specification for integration tests that require Cassandra and Cosmos Cassandra databases.
 *
 * Features:
 * - Automatically starts both Cassandra and Cosmos Cassandra using Testcontainers
 * - Provides cassandraUtils for Cassandra operations
 * - Provides cosmosCassandraUtils for Cosmos Cassandra operations
 * - Configures Spring Boot with Cassandra connection properties
 * - Shares database clients across all test methods in the spec
 * - Properly integrates with Spring's test context for autowiring
 * - Containers are automatically stopped after tests complete
 * - Both databases run on a shared Docker network for isolated testing
 *
 * Prerequisites:
 * - Docker must be installed and running
 * - No manual Docker Compose commands needed
 *
 * Usage:
 * class MyIntegrationTest extends IntegrationTestBaseCassandraSpec {
 *     def "test cassandra operations"() {
 *         when:
 *         cassandraUtils.executeQuery("SELECT * FROM system.local")
 *
 *         then:
 *         // Assert results
 *     }
 * }
 */
abstract class IntegrationTestBaseCassandraSpec extends Specification {

    @Shared
    protected static CassandraTestUtils cassandraUtils

    @Shared
    protected static CosmosCassandraTestUtils cosmosCassandraUtils

    @Shared
    protected static String cassandraContactPoint

    @Shared
    protected static int cassandraPort

    @Shared
    protected static String cosmosCassandraContactPoint

    @Shared
    protected static int cosmosCassandraPort

    @Shared
    protected static CassandraContainer cassandraContainer

    @Shared
    protected static GenericContainer cosmosCassandraContainer

    @Shared
    protected static Network cassandraNetwork

    static {
        // Static initializer block - runs BEFORE anything else
        println "\n=============================================="
        println "=== Static block: Initializing Cassandra and Cosmos Cassandra ==="
        println "=== Using: Testcontainers (Automatic Container Management) ==="
        println "==============================================\n"
        try {
            println "🔍 Starting Cassandra and Cosmos Cassandra containers..."

            // Create a shared network for containers
            cassandraNetwork = Network.newNetwork()

            // Start Cassandra
            println "📦 Starting Cassandra container..."
            cassandraContainer = new CassandraContainer(DockerImageName.parse("cassandra:4.1"))
                    .withNetwork(cassandraNetwork)
                    .withNetworkAliases("cassandra")
                    .withEnv("MAX_HEAP_SIZE", "512m")
                    .withEnv("HEAP_NEWSIZE", "256m")
                    .withExposedPorts(9042)
                    .withStartupTimeout(Duration.ofSeconds(120))

            cassandraContainer.start()
            cassandraContactPoint = cassandraContainer.getHost()
            cassandraPort = cassandraContainer.getMappedPort(9042)

            println "✅ Cassandra container started successfully"
            println "   📍 Host: ${cassandraContactPoint}"
            println "   🔌 Port: ${cassandraPort}"
            println "   🌐 Contact point: ${cassandraContactPoint}:${cassandraPort}"

            // Wait for Cassandra to be fully ready
            println "⏳ Waiting for Cassandra to be fully initialized (15 seconds)..."
            Thread.sleep(15000)

            // Verify Cassandra is running
            println "🔍 Verifying Cassandra container status..."
            def cassandraRunning = cassandraContainer.isRunning()
            if (!cassandraRunning) {
                println "❌ CRITICAL: Cassandra container is NOT running!"
                def logs = cassandraContainer.getLogs()
                println "📋 Cassandra container logs:"
                println logs
                throw new RuntimeException("Cassandra container stopped immediately after startup. Check logs above.")
            }
            println "✅ Cassandra container is running"

            // Initialize cassandraUtils
            cassandraUtils = new CassandraTestUtils(cassandraContactPoint, cassandraPort)
            println "✅ cassandraUtils initialized with: ${cassandraContactPoint}:${cassandraPort}"

            // Start Cosmos Cassandra (using Cassandra as the backend)
            println "\n📦 Starting Cosmos Cassandra container (Cassandra-based implementation)..."
            cosmosCassandraContainer = new GenericContainer(DockerImageName.parse("cassandra:4.1"))
                    .withNetwork(cassandraNetwork)
                    .withNetworkAliases("cosmos-cassandra")
                    .withEnv("CASSANDRA_DC", "cosmos-dc")
                    .withEnv("CASSANDRA_CLUSTER_NAME", "cosmos-cluster")
                    .withEnv("MAX_HEAP_SIZE", "512m")
                    .withEnv("HEAP_NEWSIZE", "256m")
                    .withExposedPorts(9042)
                    .withStartupTimeout(Duration.ofSeconds(120))

            cosmosCassandraContainer.start()
            cosmosCassandraContactPoint = cosmosCassandraContainer.getHost()
            cosmosCassandraPort = cosmosCassandraContainer.getMappedPort(9042)

            println "✅ Cosmos Cassandra container started successfully"
            println "   📍 Host: ${cosmosCassandraContactPoint}"
            println "   🔌 Port: ${cosmosCassandraPort}"
            println "   🌐 Contact point: ${cosmosCassandraContactPoint}:${cosmosCassandraPort}"

            // Wait for Cosmos Cassandra to be fully ready
            println "⏳ Waiting for Cosmos Cassandra to be fully initialized (15 seconds)..."
            Thread.sleep(15000)

            // Verify Cosmos Cassandra is running
            println "🔍 Verifying Cosmos Cassandra container status..."
            def cosmosRunning = cosmosCassandraContainer.isRunning()
            if (!cosmosRunning) {
                println "❌ CRITICAL: Cosmos Cassandra container is NOT running!"
                def logs = cosmosCassandraContainer.getLogs()
                println "📋 Cosmos Cassandra container logs:"
                println logs
                throw new RuntimeException("Cosmos Cassandra container stopped immediately after startup. Check logs above.")
            }
            println "✅ Cosmos Cassandra container is running"

            // Initialize cosmosCassandraUtils
            cosmosCassandraUtils = new CosmosCassandraTestUtils(cosmosCassandraContactPoint, cosmosCassandraPort)
            println "✅ cosmosCassandraUtils initialized with: ${cosmosCassandraContactPoint}:${cosmosCassandraPort}"

            println "\n==============================================\n"

        } catch (Exception e) {
            println "\n=============================================="
            println "❌ FATAL ERROR initializing Cassandra/Cosmos Cassandra: ${e.message}"
            println "==============================================\n"
            e.printStackTrace()
            throw new RuntimeException("Failed to initialize Cassandra/Cosmos Cassandra containers for tests", e)
        }
    }

    /**
     * Verify Cassandra and Cosmos Cassandra are running in setupSpec
     */
    def setupSpec() {
        println "\n=== setupSpec for: ${getClass().simpleName} ==="

        if (cassandraContactPoint == null) {
            println "❌ ERROR: Cassandra contact point is NULL!"
            throw new IllegalStateException("Cassandra contactPoint is null - static initialization failed!")
        }

        if (cassandraUtils == null) {
            println "❌ ERROR: cassandraUtils is NULL!"
            throw new IllegalStateException("cassandraUtils is null - static initialization failed!")
        }

        if (cosmosCassandraContactPoint == null) {
            println "❌ ERROR: Cosmos Cassandra contact point is NULL!"
            throw new IllegalStateException("Cosmos Cassandra contactPoint is null - static initialization failed!")
        }

        if (cosmosCassandraUtils == null) {
            println "❌ ERROR: cosmosCassandraUtils is NULL!"
            throw new IllegalStateException("cosmosCassandraUtils is null - static initialization failed!")
        }

        println "✅ Testcontainers-managed Cassandra instance ready"
        println "   📍 Cassandra: ${cassandraContactPoint}:${cassandraPort}"
        println "✅ Testcontainers-managed Cosmos Cassandra instance ready"
        println "   📍 Cosmos Cassandra: ${cosmosCassandraContactPoint}:${cosmosCassandraPort}"
        println "✅ cassandraUtils: ${cassandraUtils}"
        println "✅ cosmosCassandraUtils: ${cosmosCassandraUtils}"

        // Verify both Cassandra instances are ready
        println "🔍 Verifying both Cassandra instances readiness..."
        verifyCassandraReady()
        verifyCosmosReady()

        println "✅ Ready to run tests"
    }

    /**
     * Verify that Cassandra is fully initialized and ready to accept connections
     */
    private void verifyCassandraReady() {
        def maxRetries = 30
        def retryCount = 0

        println "   🔄 Starting Cassandra health check with max ${maxRetries} retries..."

        while (retryCount < maxRetries) {
            try {
                println "   📡 Checking Cassandra connectivity (attempt $retryCount/$maxRetries)..."
                def socket = new Socket()
                def address = new InetSocketAddress(cassandraContactPoint, cassandraPort)
                socket.connect(address, 5000)
                socket.close()

                println "✅ Cassandra is ready: ${cassandraContactPoint}:${cassandraPort}"
                return  // Success - exit method
            } catch (Exception e) {
                retryCount++
                if (retryCount < maxRetries) {
                    println "   ⏳ Cassandra not ready yet (${e.class.simpleName})"
                    println "      Retry attempt $retryCount/$maxRetries..."
                    Thread.sleep(2000)  // Wait 2 seconds between retries
                } else {
                    println "❌ Cassandra failed to become ready after $maxRetries attempts"
                    println "❌ Last error: ${e.class.simpleName}: ${e.message}"
                    throw new RuntimeException("Cassandra not ready after $maxRetries attempts: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Verify that Cosmos Cassandra is fully initialized and ready to accept connections
     */
    private void verifyCosmosReady() {
        def maxRetries = 30
        def retryCount = 0

        println "   🔄 Starting Cosmos Cassandra health check with max ${maxRetries} retries..."

        while (retryCount < maxRetries) {
            try {
                println "   📡 Checking Cosmos Cassandra connectivity (attempt $retryCount/$maxRetries)..."
                def socket = new Socket()
                def address = new InetSocketAddress(cosmosCassandraContactPoint, cosmosCassandraPort)
                socket.connect(address, 5000)
                socket.close()

                println "✅ Cosmos Cassandra is ready: ${cosmosCassandraContactPoint}:${cosmosCassandraPort}"
                return  // Success - exit method
            } catch (Exception e) {
                retryCount++
                if (retryCount < maxRetries) {
                    println "   ⏳ Cosmos Cassandra not ready yet (${e.class.simpleName})"
                    println "      Retry attempt $retryCount/$maxRetries..."
                    Thread.sleep(2000)  // Wait 2 seconds between retries
                } else {
                    println "❌ Cosmos Cassandra failed to become ready after $maxRetries attempts"
                    println "❌ Last error: ${e.class.simpleName}: ${e.message}"
                    throw new RuntimeException("Cosmos Cassandra not ready after $maxRetries attempts: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Cleanup after tests - automatically stops both Cassandra containers
     */
    def cleanupSpec() {
        println "\n=== cleanupSpec for: ${getClass().simpleName} ==="
        try {
            if (cassandraContainer != null) {
                println "🛑 Stopping Cassandra container..."
                cassandraContainer.stop()
                println "✅ Cassandra container stopped"
            }
            if (cosmosCassandraContainer != null) {
                println "🛑 Stopping Cosmos Cassandra container..."
                cosmosCassandraContainer.stop()
                println "✅ Cosmos Cassandra container stopped"
            }
            if (cassandraNetwork != null) {
                println "🛑 Closing Cassandra network..."
                cassandraNetwork.close()
                println "✅ Cassandra network closed"
            }
            println "✅ All containers and resources cleaned up successfully"
        } catch (Exception e) {
            println "❌ Error during cleanup: ${e.message}"
            e.printStackTrace()
        }
        println "==============================================\n"
    }

    /**
     * Dynamically inject Cassandra properties into Spring context
     * This method is called by Spring BEFORE the application context is created
     */
    @DynamicPropertySource
    static void registerCassandraProperties(DynamicPropertyRegistry registry) {
        println "\n=== @DynamicPropertySource called by Spring ==="

        if (cassandraContactPoint == null) {
            println "❌ ERROR: Cassandra contactPoint is NULL!"
            throw new IllegalStateException("Cassandra contactPoint is null - static initialization failed!")
        }

        if (cosmosCassandraContactPoint == null) {
            println "❌ ERROR: Cosmos Cassandra contactPoint is NULL!"
            throw new IllegalStateException("Cosmos Cassandra contactPoint is null - static initialization failed!")
        }

        println "✅ Registering Cassandra properties with Spring"
        println "   - Cassandra contact point: ${cassandraContactPoint}:${cassandraPort}"
        println "   - Cosmos Cassandra contact point: ${cosmosCassandraContactPoint}:${cosmosCassandraPort}"
        println "   - Using Testcontainers-managed Cassandra instances"

        // Cassandra properties
        registry.add("spring.cassandra.contact-points") { cassandraContactPoint }
        registry.add("spring.cassandra.port") { cassandraPort }
        registry.add("spring.cassandra.local-datacenter") { "datacenter1" }
        registry.add("spring.cassandra.keyspace-name") { "test_keyspace" }

        // Cosmos Cassandra properties (if your app supports multiple Cassandra instances)
        registry.add("spring.cosmos.cassandra.contact-points") { cosmosCassandraContactPoint }
        registry.add("spring.cosmos.cassandra.port") { cosmosCassandraPort }
        registry.add("spring.cosmos.cassandra.local-datacenter") { "cosmos-dc" }
        registry.add("spring.cosmos.cassandra.keyspace-name") { "cosmos_keyspace" }

        println "✅ Cassandra properties registered successfully"
    }
}

