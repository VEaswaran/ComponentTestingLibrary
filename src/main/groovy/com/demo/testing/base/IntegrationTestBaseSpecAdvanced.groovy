package com.demo.testing.base

import com.demo.testing.annotations.EnableCassandraTest
import com.demo.testing.annotations.EnableCosmosCassandraTest
import com.demo.testing.annotations.EnableKafkaTest
import com.demo.testing.annotations.EnableWireMockTest
import com.demo.testing.utils.CassandraTestUtils
import com.demo.testing.utils.CosmosCassandraTestUtils
import com.demo.testing.utils.KafkaTestUtils
import com.demo.testing.utils.WireMockTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

/**
 * Enhanced base specification for integration tests with conditional container initialization.
 *
 * Features:
 * - Automatically initializes only the containers/services marked with annotations
 * - Supports: Kafka (@EnableKafkaTest), Cassandra (@EnableCassandraTest),
 *   Cosmos DB (@EnableCosmosCassandraTest), WireMock (@EnableWireMockTest)
 * - Each annotation is independent - only enabled annotations are initialized
 * - Provides utility classes for each service
 *
 * Usage:
 * @EnableKafkaTest
 * @EnableCassandraTest
 * class MyIntegrationTest extends IntegrationTestBaseSpecAdvanced { ... }
 */
abstract class IntegrationTestBaseSpecAdvanced extends Specification {

    // Kafka
    @Shared
    protected static KafkaContainer kafkaContainer
    @Shared
    protected static KafkaTestUtils kafkaUtils
    @Shared
    protected static String kafkaBootstrapServers

    // Cassandra
    @Shared
    protected static CassandraContainer cassandraContainer
    @Shared
    protected static CassandraTestUtils cassandraUtils
    @Shared
    protected static String cassandraContactPoint
    @Shared
    protected static int cassandraPort

    // Cosmos Cassandra
    @Shared
    protected static GenericContainer cosmosCassandraContainer
    @Shared
    protected static CosmosCassandraTestUtils cosmosCassandraUtils
    @Shared
    protected static String cosmosCassandraContactPoint
    @Shared
    protected static int cosmosCassandraPort

    // WireMock
    @Shared
    protected static GenericContainer wireMockContainer
    @Shared
    protected static WireMockTestUtils wireMockUtils
    @Shared
    protected static String wireMockUrl
    @Shared
    protected static int wireMockPort

    static {
        println "\n=============================================="
        println "=== Advanced IntegrationTestBaseSpec Initialization ==="
        println "==============================================\n"

        try {
            // Check for annotations
            def testClass = null
            // Get the test class dynamically
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace()
            for (element in stackTrace) {
                if (element.className.contains("ComponentSpec") || element.className.contains("Spec")) {
                    try {
                        testClass = Class.forName(element.className)
                        break
                    } catch (Exception ignored) {
                    }
                }
            }

            // Initialize Kafka if annotation is present
            if (testClass?.getAnnotation(EnableKafkaTest)) {
                println "📌 @EnableKafkaTest detected - Initializing Kafka..."
                initializeKafka()
            } else {
                println "⊘ @EnableKafkaTest not found - Kafka will NOT be initialized"
            }

            // Initialize Cassandra if annotation is present
            if (testClass?.getAnnotation(EnableCassandraTest)) {
                println "📌 @EnableCassandraTest detected - Initializing Cassandra..."
                initializeCassandra()
            } else {
                println "⊘ @EnableCassandraTest not found - Cassandra will NOT be initialized"
            }

            // Initialize Cosmos Cassandra if annotation is present
            if (testClass?.getAnnotation(EnableCosmosCassandraTest)) {
                println "📌 @EnableCosmosCassandraTest detected - Initializing Cosmos Cassandra..."
                initializeCosmosCassandra()
            } else {
                println "⊘ @EnableCosmosCassandraTest not found - Cosmos Cassandra will NOT be initialized"
            }

            // Initialize WireMock if annotation is present
            if (testClass?.getAnnotation(EnableWireMockTest)) {
                println "📌 @EnableWireMockTest detected - Initializing WireMock..."
                initializeWireMock()
            } else {
                println "⊘ @EnableWireMockTest not found - WireMock will NOT be initialized"
            }

            println "\n==============================================\n"

        } catch (Exception e) {
            println "\n=============================================="
            println "❌ FATAL ERROR initializing containers: ${e.message}"
            println "==============================================\n"
            e.printStackTrace()
            throw new RuntimeException("Failed to initialize test containers", e)
        }
    }

    /**
     * Initialize Kafka TestContainer
     */
    private static void initializeKafka() {
        try {
            def localKafka = "localhost:9092"
            println "🔍 Checking if Kafka is already running on ${localKafka}..."

            if (isServiceRunning(localKafka)) {
                println "✅ Found existing Kafka instance"
                kafkaBootstrapServers = localKafka
                kafkaContainer = null
            } else {
                println "🚀 Starting Kafka TestContainer..."
                kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                kafkaContainer.withExposedPorts(9092)
                kafkaContainer.start()
                kafkaBootstrapServers = kafkaContainer.bootstrapServers
                println "✅ Kafka container STARTED: ${kafkaBootstrapServers}"
            }

            kafkaUtils = new KafkaTestUtils(kafkaBootstrapServers)
            println "✅ kafkaUtils initialized"

        } catch (Exception e) {
            println "❌ ERROR initializing Kafka: ${e.message}"
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Initialize Cassandra TestContainer
     */
    private static void initializeCassandra() {
        try {
            println "🚀 Starting Cassandra TestContainer..."
            cassandraContainer = new CassandraContainer(DockerImageName.parse("cassandra:4.0"))
            cassandraContainer.withExposedPorts(9042)
            cassandraContainer.start()

            cassandraContactPoint = cassandraContainer.getHost()
            cassandraPort = cassandraContainer.getMappedPort(9042)

            cassandraUtils = new CassandraTestUtils(cassandraContactPoint, cassandraPort)
            println "✅ Cassandra container STARTED: ${cassandraContactPoint}:${cassandraPort}"
            println "✅ cassandraUtils initialized"

        } catch (Exception e) {
            println "❌ ERROR initializing Cassandra: ${e.message}"
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Initialize Cosmos Cassandra (using generic container as TestContainer doesn't have built-in support)
     * Note: For testing, you may want to use Cassandra container as a substitute or mock Cosmos DB
     */
    private static void initializeCosmosCassandra() {
        try {
            println "🚀 Starting Cosmos Cassandra (using Cassandra as substitute)..."
            // For local testing, we use Cassandra container; in production use actual Cosmos DB
            cosmosCassandraContainer = new GenericContainer(DockerImageName.parse("cassandra:4.1"))
            cosmosCassandraContainer.withExposedPorts(9042)
            cosmosCassandraContainer.start()

            cosmosCassandraContactPoint = cosmosCassandraContainer.getHost()
            cosmosCassandraPort = cosmosCassandraContainer.getMappedPort(9042)

            cosmosCassandraUtils = new CosmosCassandraTestUtils(cosmosCassandraContactPoint, cosmosCassandraPort)
            println "✅ Cosmos Cassandra container STARTED: ${cosmosCassandraContactPoint}:${cosmosCassandraPort}"
            println "✅ cosmosCassandraUtils initialized"

        } catch (Exception e) {
            println "❌ ERROR initializing Cosmos Cassandra: ${e.message}"
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Initialize WireMock TestContainer
     */
    private static void initializeWireMock() {
        try {
            println "🚀 Starting WireMock TestContainer..."
            wireMockContainer = new GenericContainer(DockerImageName.parse("wiremock/wiremock:3.1.0"))
            wireMockContainer.withExposedPorts(8080)
            wireMockContainer.start()

            wireMockUrl = "http://" + wireMockContainer.getHost()
            wireMockPort = wireMockContainer.getMappedPort(8080)

            wireMockUtils = new WireMockTestUtils(wireMockUrl, wireMockPort)
            println "✅ WireMock container STARTED: ${wireMockUrl}:${wireMockPort}"
            println "✅ wireMockUtils initialized"

        } catch (Exception e) {
            println "❌ ERROR initializing WireMock: ${e.message}"
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Check if a service is running
     */
    private static boolean isServiceRunning(String servers) {
        try {
            def socket = new Socket()
            def address = new InetSocketAddress("localhost", 9092)
            socket.connect(address, 2000)
            socket.close()
            return true
        } catch (Exception e) {
            return false
        }
    }

    def setupSpec() {
        println "\n=== setupSpec for: ${getClass().simpleName} ==="

        if (kafkaContainer) {
            println "✅ Kafka TestContainer verified running"
        }
        if (cassandraContainer) {
            println "✅ Cassandra TestContainer verified running"
        }
        if (cosmosCassandraContainer) {
            println "✅ Cosmos Cassandra container verified running"
        }
        if (wireMockContainer) {
            println "✅ WireMock container verified running"
        }
    }

    def cleanupSpec() {
        println "\n=== cleanupSpec: Cleaning up containers ==="

        if (kafkaContainer?.isRunning()) {
            println "Stopping Kafka TestContainer..."
            kafkaContainer.stop()
        }
        if (cassandraContainer?.isRunning()) {
            println "Stopping Cassandra TestContainer..."
            cassandraContainer.stop()
        }
        if (cosmosCassandraContainer?.isRunning()) {
            println "Stopping Cosmos Cassandra container..."
            cosmosCassandraContainer.stop()
        }
        if (wireMockContainer?.isRunning()) {
            println "Stopping WireMock container..."
            wireMockContainer.stop()
        }

        // Close Cassandra session
        if (cassandraUtils) {
            cassandraUtils.closeSession()
        }
        if (cosmosCassandraUtils) {
            cosmosCassandraUtils.closeSession()
        }
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        println "\n=== @DynamicPropertySource called by Spring ==="

        if (kafkaBootstrapServers) {
            println "Registering Kafka bootstrap servers: ${kafkaBootstrapServers}"
            registry.add("spring.kafka.bootstrap-servers") { kafkaBootstrapServers }
            registry.add("spring.kafka.consumer.bootstrap-servers") { kafkaBootstrapServers }
            registry.add("spring.kafka.producer.bootstrap-servers") { kafkaBootstrapServers }
        }

        if (cassandraContactPoint) {
            println "Registering Cassandra contact point: ${cassandraContactPoint}:${cassandraPort}"
            registry.add("spring.data.cassandra.contact-points") { cassandraContactPoint }
            registry.add("spring.data.cassandra.port") { cassandraPort.toString() }
        }

        if (cosmosCassandraContactPoint) {
            println "Registering Cosmos Cassandra contact point: ${cosmosCassandraContactPoint}:${cosmosCassandraPort}"
            registry.add("spring.data.cosmos.cassandra.contact-points") { cosmosCassandraContactPoint }
            registry.add("spring.data.cosmos.cassandra.port") { cosmosCassandraPort.toString() }
        }

        if (wireMockPort) {
            println "Registering WireMock URL: ${wireMockUrl}:${wireMockPort}"
            registry.add("wiremock.url") { wireMockUrl }
            registry.add("wiremock.port") { wireMockPort.toString() }
        }

        println "✅ Dynamic properties registered successfully"
    }
}

