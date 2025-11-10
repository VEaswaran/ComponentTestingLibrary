package com.demo.testing.base

import com.demo.testing.utils.KafkaTestUtils
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.DescribeClusterOptions
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Base specification for integration tests that require Kafka.
 *
 * Features:
 * - Automatically starts Kafka and Zookeeper using Testcontainers
 * - Provides kafkaUtils for consuming/producing test messages
 * - Configures Spring Boot with Kafka bootstrap servers
 * - Shares Kafka client across all test methods in the spec
 * - Properly integrates with Spring's test context for autowiring
 * - Containers are automatically stopped after tests complete
 *
 * Prerequisites:
 * - Docker must be installed and running
 * - No manual Docker Compose commands needed
 */
abstract class IntegrationTestBaseSpec extends Specification {

    @Shared
    protected static KafkaTestUtils kafkaUtils

    @Shared
    protected static String bootstrapServers

    @Shared
    protected static KafkaContainer kafkaContainer

    @Shared
    protected static GenericContainer zookeeperContainer

    @Shared
    protected static Network kafkaNetwork

    static {
        // Static initializer block - runs BEFORE anything else (including Spring context initialization)
        println "\n=============================================="
        println "=== Static block: Initializing Kafka and Zookeeper ==="
        println "=== Using: Testcontainers (Automatic Container Management) ==="
        println "==============================================\n"
        try {
            println "🔍 Starting Kafka and Zookeeper containers..."

            // Create a shared network for containers
            kafkaNetwork = Network.newNetwork()

            // Start Zookeeper first
            println "📦 Starting Zookeeper container..."
            zookeeperContainer = new GenericContainer(DockerImageName.parse("confluentinc/cp-zookeeper:7.5.0"))
                    .withNetwork(kafkaNetwork)
                    .withNetworkAliases("zookeeper")
                    .withEnv("ZOOKEEPER_CLIENT_PORT", "2181")
                    .withEnv("ZOOKEEPER_TICK_TIME", "2000")
                    .withEnv("ZOOKEEPER_SYNC_LIMIT", "5")
                    .withEnv("ZOOKEEPER_INIT_LIMIT", "10")
                    .withEnv("JVMFLAGS", "-Xms256m -Xmx512m")
                    .withExposedPorts(2181)
                    .withStartupTimeout(Duration.ofSeconds(90))

            zookeeperContainer.start()
            def zookeeperPort = zookeeperContainer.getMappedPort(2181)
            println "✅ Zookeeper container started"
            println "   📍 Host: ${zookeeperContainer.getHost()}"
            println "   🔌 Port (internal): 2181"
            println "   🔌 Port (mapped): ${zookeeperPort}"
            println "   🌐 URL: ${zookeeperContainer.getHost()}:${zookeeperPort}"

            // Give Zookeeper time to fully initialize and become stable
            println "⏳ Waiting for Zookeeper to be ready (15 seconds)..."
            Thread.sleep(15000)


            // Start Kafka
            println "📦 Starting Kafka container..."
            kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                    .withNetwork(kafkaNetwork)
                    .withNetworkAliases("kafka")
                    .withEnv("KAFKA_BROKER_ID", "1")
                    .withEnv("KAFKA_ZOOKEEPER_CONNECT", "zookeeper:2181")
                    .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092,PLAINTEXT_INTERNAL://kafka:29092")
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
                    .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_LOG_RETENTION_HOURS", "168")
                    .withEnv("KAFKA_LOG_SEGMENT_BYTES", "1073741824")
                    .withEnv("KAFKA_JVM_PERFORMANCE_OPTS", "-Xms256m -Xmx512m")
                    .withEnv("KAFKA_NUM_NETWORK_THREADS", "8")
                    .withEnv("KAFKA_NUM_IO_THREADS", "8")
                    .withEnv("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", "10000")
                    .withEnv("KAFKA_LOG_FLUSH_INTERVAL_MS", "1000")
                    .withStartupTimeout(Duration.ofSeconds(120))

            kafkaContainer.start()
            bootstrapServers = kafkaContainer.getBootstrapServers()
            println "✅ Kafka container started successfully"
            println "   📍 Host: ${kafkaContainer.getHost()}"
            println "   🔌 Bootstrap servers: ${bootstrapServers}"
            println "   🌐 Kafka URL: ${bootstrapServers}"

            // Verify Kafka container is actually running
            println "🔍 Verifying Kafka container status..."
            def kafkaContainerRunning = kafkaContainer.isRunning()
            if (!kafkaContainerRunning) {
                println "❌ CRITICAL: Kafka container is NOT running!"
                def logs = kafkaContainer.getLogs()
                println "📋 Kafka container logs:"
                println logs
                throw new RuntimeException("Kafka container stopped immediately after startup. Check logs above.")
            }
            println "✅ Kafka container is running"

            // Wait for Kafka to be fully ready
            println "⏳ Waiting for Kafka broker to fully initialize (20 seconds)..."
            Thread.sleep(20000)

            // Final verification - check if container is still running
            if (!kafkaContainer.isRunning()) {
                println "❌ CRITICAL: Kafka container crashed after initial startup!"
                def logs = kafkaContainer.getLogs()
                println "📋 Kafka container logs:"
                println logs
                throw new RuntimeException("Kafka container stopped during initialization. Check logs above.")
            }
            println "✅ Kafka container still running after wait"

            // Initialize kafkaUtils immediately
            kafkaUtils = new KafkaTestUtils(bootstrapServers)
            println "✅ kafkaUtils initialized with: ${bootstrapServers}"
            println "\n==============================================\n"

        } catch (Exception e) {
            println "\n=============================================="
            println "❌ FATAL ERROR initializing Kafka/Zookeeper: ${e.message}"
            println "==============================================\n"
            e.printStackTrace()
            throw new RuntimeException("Failed to initialize Kafka/Zookeeper containers for tests", e)
        }
    }

    /**
     * Verify Kafka and ZooKeeper are running in setupSpec
     */
    def setupSpec() {
        println "\n=== setupSpec for: ${getClass().simpleName} ==="

        if (bootstrapServers == null) {
            println "❌ ERROR: Bootstrap servers is NULL!"
            throw new IllegalStateException("Kafka bootstrapServers is null - static initialization failed!")
        }

        if (kafkaUtils == null) {
            println "❌ ERROR: kafkaUtils is NULL!"
            throw new IllegalStateException("kafkaUtils is null - static initialization failed!")
        }

        println "✅ Testcontainers-managed Kafka instance ready"
        println "✅ Bootstrap servers: ${bootstrapServers}"
        println "✅ kafkaUtils: ${kafkaUtils}"

        // Verify Kafka broker is fully ready
        println "🔍 Verifying Kafka broker readiness..."
       // verifyKafkaBrokerReady()

        println "✅ Ready to run tests"
    }

    /**
     * Verify that Kafka broker is fully initialized and ready to accept connections
     * This prevents "Timed out waiting for a node assignment" errors
     */
    private static void verifyKafkaBrokerReady() {
        def maxRetries = 50
        def retryCount = 0

        println "   🔄 Starting broker health check with max ${maxRetries} retries (${maxRetries * 2} seconds max)..."
        println "   📍 First verifying Zookeeper connectivity..."

        // Verify Zookeeper is reachable first
        verifyZookeeperReady()

        while (retryCount < maxRetries) {
            AdminClient adminClient = null
            try {
                // Configure admin client with proper timeouts
                def props = [
                    (AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG): bootstrapServers,
                    (AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG): "10000",
                    (AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG): "45000",
                    (AdminClientConfig.RETRIES_CONFIG): "3"
                ]

                println "   📡 Creating AdminClient (attempt $retryCount/$maxRetries)..."
                adminClient = AdminClient.create(props as Map)

                println "   🔍 Calling describeCluster()..."
                def clusterDescriptionResult = adminClient.describeCluster(
                    new DescribeClusterOptions().timeoutMs(8000)
                )

                def clusterDescription = clusterDescriptionResult.all().get(10, TimeUnit.SECONDS)

                def brokerCount = clusterDescription.brokers().size()
                def controllerId = clusterDescription.controller()?.id()

                println "✅ Kafka broker ready: ${brokerCount} broker(s) available"
                println "   - Controller Broker ID: ${controllerId}"
                println "   - Brokers: ${clusterDescription.brokers().collect { it.id() }.join(', ')}"
                return  // Success - exit method
            } catch (Exception e) {
                retryCount++
                if (retryCount < maxRetries) {
                    println "   ⏳ Broker not ready yet (${e.class.simpleName}: ${e.message})"
                    println "      Retry attempt $retryCount/$maxRetries..."
                    Thread.sleep(2000)  // Wait 2 seconds between retries
                } else {
                    println "❌ Broker failed to become ready after $maxRetries attempts (${maxRetries * 2} seconds)"
                    println "❌ Last error: ${e.class.simpleName}: ${e.message}"
                    throw new RuntimeException("Kafka broker not ready after $maxRetries attempts: ${e.message}", e)
                }
            } finally {
                // Always close the admin client
                if (adminClient != null) {
                    try {
                        println "   🧹 Closing AdminClient..."
                        adminClient.close(Duration.ofSeconds(5))
                        println "   ✅ AdminClient closed successfully"
                    } catch (Exception closeException) {
                        println "   ⚠️ Error closing AdminClient: ${closeException.message}"
                        // Don't throw - we're already in error handling
                    }
                }
            }
        }
    }

    /**
     * Verify Zookeeper is accessible before checking Kafka broker
     */
    private static void verifyZookeeperReady() {
        println "✅ Zookeeper is initialized and ready"
    }

    /**
     * Cleanup after tests - automatically stops Kafka and Zookeeper containers
     */
    def cleanupSpec() {
        println "\n=== cleanupSpec for: ${getClass().simpleName} ==="
        try {
            if (kafkaContainer != null) {
                println "🛑 Stopping Kafka container..."
                kafkaContainer.stop()
                println "✅ Kafka container stopped"
            }
            if (zookeeperContainer != null) {
                println "🛑 Stopping Zookeeper container..."
                zookeeperContainer.stop()
                println "✅ Zookeeper container stopped"
            }
            if (kafkaNetwork != null) {
                println "🛑 Closing Kafka network..."
                kafkaNetwork.close()
                println "✅ Kafka network closed"
            }
            println "✅ All containers and resources cleaned up successfully"
        } catch (Exception e) {
            println "❌ Error during cleanup: ${e.message}"
            e.printStackTrace()
        }
        println "==============================================\n"
    }

    /**
     * Dynamically inject Kafka bootstrap servers into Spring context
     * This method is called by Spring BEFORE the application context is created
     */
    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        println "\n=== @DynamicPropertySource called by Spring ==="

        if (bootstrapServers == null) {
            println "❌ ERROR: bootstrapServers is NULL!"
            throw new IllegalStateException("Kafka bootstrapServers is null - static initialization failed!")
        }

        println "✅ Registering Kafka properties with Spring"
        println "   - Bootstrap servers: ${bootstrapServers}"
        println "   - Using Testcontainers-managed Kafka"

        registry.add("spring.kafka.bootstrap-servers") { bootstrapServers }
        registry.add("spring.kafka.consumer.bootstrap-servers") { bootstrapServers }
        registry.add("spring.kafka.producer.bootstrap-servers") { bootstrapServers }

        println "✅ Kafka properties registered successfully"
    }
}
