package com.demo.testing.base

import com.demo.testing.utils.CosmosNoSqlTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.time.Duration

/**
 * Base specification for integration tests that require Azure Cosmos DB (NoSQL API).
 *
 * Features:
 * - Automatically starts Cosmos DB Emulator using Testcontainers
 * - Provides cosmosNoSqlUtils for Cosmos DB NoSQL operations
 * - Configures Spring Boot with Cosmos DB connection properties
 * - Shares database client across all test methods in the spec
 * - Properly integrates with Spring's test context for autowiring
 * - Container is automatically stopped after tests complete
 * - Runs on isolated Docker network for clean testing environment
 *
 * Prerequisites:
 * - Docker must be installed and running
 * - Windows or Linux with enough resources (Cosmos Emulator is resource-intensive)
 * - No manual Docker Compose commands needed
 *
 * Usage:
 * class MyCosmosIntegrationTest extends IntegrationTestBaseCosmosNoSqlSpec {
 *     def "test cosmos nosql operations"() {
 *         when:
 *         def utils = cosmosNoSqlUtils
 *         def container = utils.getOrCreateContainer("testdb", "testcol")
 *
 *         then:
 *         container != null
 *     }
 * }
 *
 * Docker Image:
 * - mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator (Linux Emulator)
 * - Port: 8081 (HTTPS endpoint)
 * - Port: 10251-10256 (Internal replication ports)
 */
abstract class IntegrationTestBaseCosmosNoSqlSpec extends Specification {

    @Shared
    protected static CosmosNoSqlTestUtils cosmosNoSqlUtils

    @Shared
    protected static String cosmosEndpoint

    @Shared
    protected static String cosmosKey

    @Shared
    protected static GenericContainer cosmosContainer

    @Shared
    protected static Network cosmosNetwork

    /**
     * Disable SSL certificate validation for Cosmos DB Emulator
     * The emulator uses self-signed certificates which are not trusted by default
     */
    static void disableSSLCertificateValidation() {
        try {
            println "🔒 Disabling SSL certificate validation for Cosmos DB Emulator (self-signed certificates)..."

            // Create a trust manager that accepts all certificates
            def trustAllCerts = [
                new X509TrustManager() {
                    X509Certificate[] getAcceptedIssuers() { null }
                    void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            ] as TrustManager[]

            // Install the all-trusting trust manager
            def sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom())
            SSLContext.setDefault(sslContext)

            // Disable hostname verification
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { hostname, session -> true }

            println "✅ SSL certificate validation disabled successfully"
        } catch (Exception e) {
            println "⚠️ Warning: Could not disable SSL certificate validation: ${e.message}"
            println "   This may cause certificate validation errors with Cosmos Emulator"
        }
    }

    static {
        // Static initializer block - runs BEFORE anything else
        println "\n=============================================="
        println "=== Static block: Initializing Cosmos NoSQL DB ==="
        println "=== Using: Cosmos DB Emulator (Docker) ==="
        println "==============================================\n"

        // Check for environment variable to use remote Cosmos DB
        String remoteEndpoint = System.getenv("COSMOS_ENDPOINT")
        String remoteKey = System.getenv("COSMOS_KEY")

        if (remoteEndpoint != null && remoteKey != null) {
            println "✅ Using REMOTE Cosmos DB instance (from environment variables)"
            println "   Endpoint: ${remoteEndpoint}"
            cosmosEndpoint = remoteEndpoint
            cosmosKey = remoteKey
            cosmosNoSqlUtils = new CosmosNoSqlTestUtils(cosmosEndpoint, cosmosKey)
            println "✅ Remote Cosmos DB configured - skipping Docker container"
            return
        }

        try {
            // Disable SSL certificate validation for Cosmos Emulator
            disableSSLCertificateValidation()

            println "🔍 Checking Docker daemon status..."
            verifyDockerAvailable()

            println "📦 Pulling Cosmos DB Emulator image..."
            pullCosmosImage()

            println "📦 Starting Cosmos DB Emulator container..."

            // Create a network for the container
            cosmosNetwork = Network.newNetwork()

            // Start Cosmos DB Emulator with better resource management
            String cosmosImageName = "mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest"
            println "📦 Using image: ${cosmosImageName}"

            cosmosContainer = new GenericContainer(DockerImageName.parse(cosmosImageName))
                    .withNetwork(cosmosNetwork)
                    .withNetworkAliases("cosmos-nosql")
                    .withEnv("COSMOS_DB_EMULATOR_PARTITION_COUNT", "1")
                    .withEnv("AZURE_COSMOS_EMULATOR_ENABLE_DATA_EXPLORER", "true")
                    .withExposedPorts(8081, 10251, 10252, 10253, 10254, 10255, 10256)
                    .withStartupTimeout(Duration.ofSeconds(300))
                    .withCreateContainerCmdModifier { cmd ->
                        // Configure container with proper settings for Cosmos Emulator
                        cmd.getHostConfig()
                            .withMemory(4_000_000_000L)   // 4GB memory (minimum for Cosmos)
                            .withCpuCount(2L)              // 2 CPUs
                            .withMemorySwap(4_000_000_000L)
                            .withPrivileged(true)          // Required for emulator on some hosts
                    }
                    .waitingFor(
                        Wait.forLogMessage(".*Emulator.*successfully.*|.*listening.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(240))
                    )

            println "📦 Container configured. Starting..."
            cosmosContainer.start()
            println "✅ Container started"

            // Get the endpoint and key
            def rawHost = cosmosContainer.getHost()
            def port = cosmosContainer.getMappedPort(8081)

            // On Windows with Docker Desktop, use 127.0.0.1 for external connections
            // getHost() may return Docker internal hostname which won't resolve correctly
            def host = "127.0.0.1"

            // Only use getHost() if it's a valid IP or localhost
            if (rawHost != null && (rawHost.contains(".") || rawHost.equals("localhost"))) {
                host = rawHost
            }

            // The mapped port is the correct way to access the container
            cosmosEndpoint = "https://${host}:${port}"
            cosmosKey = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTjchw5LvMR0CNLio7QA/5DB/TcxwKeVw=="

            println "✅ Cosmos DB Emulator container started successfully"
            println "   📍 Raw Host (from container): ${rawHost}"
            println "   📍 Resolved Host: ${host}"
            println "   🔌 Mapped Port (External): ${port}"
            println "   🌐 Endpoint: ${cosmosEndpoint}"
            println "   📌 Internal Container Port: 8081"

            // Verify the port is actually mapped
            if (port == null || port == 0) {
                println "❌ ERROR: Port mapping failed! Port is: ${port}"
                println "   This usually happens when the container fails to start properly"
                def logs = cosmosContainer.getLogs()
                println "📋 Container logs:"
                println logs
                throw new RuntimeException("Port 8081 not properly mapped to external port")
            }
            println "✅ Port mapping verified: 8081 -> ${port}"

            // Wait for Cosmos DB to be fully ready
            println "⏳ Waiting for Cosmos DB Emulator to be fully initialized (60 seconds)..."
            Thread.sleep(60000)

            // Verify Cosmos DB is running
            println "🔍 Verifying Cosmos DB Emulator container status..."
            def cosmosRunning = cosmosContainer.isRunning()
            if (!cosmosRunning) {
                println "❌ CRITICAL: Cosmos DB Emulator container is NOT running!"
                def logs = cosmosContainer.getLogs()
                println "📋 Cosmos DB Emulator container logs:"
                println logs
                throw new RuntimeException("Cosmos DB Emulator container stopped immediately after startup. Check logs above.")
            }
            println "✅ Cosmos DB Emulator container is running"

            // Initialize cosmosNoSqlUtils
            cosmosNoSqlUtils = new CosmosNoSqlTestUtils(cosmosEndpoint, cosmosKey)
            println "✅ cosmosNoSqlUtils initialized with: ${cosmosEndpoint}"

            // Test the connection before proceeding
            println "🔍 Testing network connectivity to endpoint: ${cosmosEndpoint}"
            testConnectionToEndpoint(cosmosEndpoint)

            println "\n==============================================\n"

        } catch (Exception e) {
            println "\n=============================================="
            println "❌ FATAL ERROR initializing Cosmos NoSQL DB"
            println "==============================================\n"
            println "📋 ERROR DETAILS:"
            println "${e.class.name}: ${e.message}"

            // Print stack trace for debugging
            println "\n🔍 STACK TRACE:"
            e.printStackTrace()

            // Print Docker diagnostics
            printDockerDiagnostics()

            // Try to get container logs
            if (cosmosContainer != null && cosmosContainer.isRunning()) {
                println "\n📋 CONTAINER LOGS:"
                try {
                    println cosmosContainer.getLogs()
                } catch (Exception logE) {
                    println "(Could not retrieve logs: ${logE.message})"
                }
            }

            // Print troubleshooting information
            println "\n📋 TROUBLESHOOTING CHECKLIST:"
            println "1. ✓ Ensure Docker daemon is running:"
            println "     Windows/Mac: Start Docker Desktop"
            println "     Linux: sudo systemctl start docker"
            println ""
            println "2. ✓ Verify Docker resources:"
            println "     - Minimum: 4GB RAM, 10GB free disk space"
            println "     - Recommended: 8GB+ RAM"
            println "     - Windows: Use WSL2 backend, not Hyper-V"
            println ""
            println "3. ✓ Test Docker manually:"
            println "     docker pull mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest"
            println "     docker run --name cosmos-test -p 8081:8081 -m 3g --cpus=2 \\"
            println "       mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest"
            println ""
            println "4. ✓ Check network connectivity:"
            println "     ping localhost"
            println "     telnet localhost 8081"
            println ""
            println "5. ✓ Review container logs:"
            println "     docker logs cosmos-nosql"
            println ""
            println "6. ✓ Alternative: Use remote Cosmos DB:"
            println "     Update cosmosEndpoint and cosmosKey in test configuration"
            println ""
            println "7. ✓ Windows-specific:"
            println "     - Check if Docker is using WSL2 backend:"
            println "       Docker Desktop > Settings > Resources > WSL integration"
            println "     - Ensure Hyper-V is enabled"
            println ""

            throw new RuntimeException("Failed to initialize Cosmos NoSQL DB Emulator for tests: ${e.message}", e)
        }
    }

    /**
     * Verify Docker daemon is available
     */
    private static void verifyDockerAvailable() {
        try {
            def process = "docker ps".execute()
            process.waitFor()
            if (process.exitValue() != 0) {
                throw new RuntimeException("Docker daemon is not responding")
            }
            println "✅ Docker daemon is available"
        } catch (Exception e) {
            println "❌ Docker daemon is NOT available"
            println "   Error: ${e.message}"
            throw new RuntimeException("Docker daemon is not available. Please start Docker and try again.", e)
        }
    }

    /**
     * Pull Cosmos DB Emulator image
     */
    private static void pullCosmosImage() {
        try {
            String imageName = "mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator"
            String imageTag = "latest"
            String fullImageName = "${imageName}:${imageTag}"

            println "🔍 Checking if Cosmos DB Emulator image exists..."
            def checkProcess = "docker images ${imageName}".execute()
            checkProcess.waitFor()
            def output = checkProcess.text

            if (output.contains("azure-cosmos-emulator")) {
                println "✅ Cosmos DB Emulator image already cached locally"
            } else {
                println "📥 Pulling Cosmos DB Emulator image (this may take 5-15 minutes)..."
                println "   Full image: ${fullImageName}"

                // Try to login to MCR first (no credentials needed for public images)
                println "🔐 Attempting to login to MCR (mcr.microsoft.com)..."
                try {
                    def loginProcess = "docker login -u 00000000-0000-0000-0000-000000000000 --password-stdin mcr.microsoft.com".execute()
                    loginProcess.getOutputStream().write("\n".getBytes())
                    loginProcess.getOutputStream().close()
                    loginProcess.waitFor()
                    // Login failure is not critical - MCR public images don't need auth
                    if (loginProcess.exitValue() == 0) {
                        println "✅ MCR login successful"
                    } else {
                        println "ℹ️ MCR login not required for public images"
                    }
                } catch (Exception ignored) {
                    println "ℹ️ MCR login skipped (public images don't require authentication)"
                }

                // Pull the image
                println "📦 Pulling image..."
                def pullProcess = "docker pull ${fullImageName}".execute()

                // Capture output in real-time
                def pullOutput = new StringBuilder()
                def pullError = new StringBuilder()

                pullProcess.inputStream.eachLine { line ->
                    println "   ${line}"
                    pullOutput.append(line).append("\n")
                }

                pullProcess.errorStream.eachLine { line ->
                    println "   ⚠️ ${line}"
                    pullError.append(line).append("\n")
                }

                pullProcess.waitFor()

                if (pullProcess.exitValue() == 0) {
                    println "✅ Image pulled successfully"
                } else {
                    println "⚠️ Warning: Image pull returned exit code ${pullProcess.exitValue()}"

                    // Check if it's a credential error
                    def errorMsg = pullError.toString() + pullOutput.toString()
                    if (errorMsg.contains("credential") || errorMsg.contains("unauthorized") || errorMsg.contains("authentication")) {
                        println "ℹ️ Note: Credential error detected, but MCR public images don't need authentication"
                        println "   Docker may retry automatically, or the image may already be cached"
                        println "   Proceeding with Testcontainers pull as fallback..."
                    } else {
                        println "   Output: ${pullOutput.toString()}"
                    }
                    // Continue anyway - Testcontainers will attempt to pull automatically
                }
            }
        } catch (Exception e) {
            println "⚠️ Warning: Could not verify/pull Cosmos DB image"
            println "   Error: ${e.message}"
            println "   Note: Testcontainers will attempt to pull the image automatically"
            println "   If you continue to see credential errors:"
            println "   - The image may be cached locally already"
            println "   - Docker Desktop may need to be restarted"
            println "   - Check: docker images | grep cosmosdb"
            // Don't throw - let Testcontainers attempt to pull automatically
        }
    }

    /**
     * Print detailed Docker and container diagnostics for troubleshooting
     */
    private static void printDockerDiagnostics(String containerName = "cosmos-nosql") {
        println "\n📋 DOCKER DIAGNOSTICS:"
        try {
            println "   🔍 Running: docker ps -a"
            def psProcess = "docker ps -a".execute()
            psProcess.waitFor()
            println psProcess.text
        } catch (Exception e) {
            println "   ❌ Could not run docker ps: ${e.message}"
        }

        try {
            println "   🔍 Running: docker inspect (looking for our container)"
            def inspectProcess = "docker ps -a --format '{{.Names}}\\t{{.Ports}}\\t{{.Status}}'".execute()
            inspectProcess.waitFor()
            println inspectProcess.text
        } catch (Exception e) {
            println "   ❌ Could not run docker inspect: ${e.message}"
        }

        if (cosmosContainer != null) {
            try {
                println "   🔍 Container ID: ${cosmosContainer.containerId}"
                println "   🔍 Container Port 8081 mapped to: ${cosmosContainer.getMappedPort(8081)}"
                println "   🔍 Container running: ${cosmosContainer.isRunning()}"
                println "   🔍 Container logs (last 50 lines):"
                def allLogs = cosmosContainer.getLogs()
                def lines = allLogs.split('\n')
                def lastLines = lines.length > 50 ? lines[-50..-1] : lines
                lastLines.each { println "        $it" }
            } catch (Exception e) {
                println "   ❌ Error getting container info: ${e.message}"
            }
        }
    }

    /**
     * Verify Cosmos DB is running in setupSpec
     */
    def setupSpec() {
        println "\n=== setupSpec for: ${getClass().simpleName} ==="

        if (cosmosEndpoint == null) {
            println "❌ ERROR: Cosmos endpoint is NULL!"
            throw new IllegalStateException("Cosmos endpoint is null - static initialization failed!")
        }

        if (cosmosKey == null) {
            println "❌ ERROR: Cosmos key is NULL!"
            throw new IllegalStateException("Cosmos key is null - static initialization failed!")
        }

        if (cosmosNoSqlUtils == null) {
            println "❌ ERROR: cosmosNoSqlUtils is NULL!"
            throw new IllegalStateException("cosmosNoSqlUtils is null - static initialization failed!")
        }

        println "✅ Testcontainers-managed Cosmos NoSQL DB instance ready"
        println "   📍 Endpoint: ${cosmosEndpoint}"
        println "   🔑 Key: ${cosmosKey.take(10)}..."
        println "✅ cosmosNoSqlUtils: ${cosmosNoSqlUtils}"

        // Verify Cosmos DB is ready
        println "🔍 Verifying Cosmos DB readiness..."
        verifyCosmosReady()

        println "✅ Ready to run tests"
    }

    /**
     * Verify that Cosmos DB is fully initialized and ready to accept connections
     */
    private void verifyCosmosReady() {
        def maxRetries = 30
        def retryCount = 0

        println "   🔄 Starting Cosmos DB health check with max ${maxRetries} retries..."
        println "   📍 Testing endpoint: ${cosmosEndpoint}"
        println "   🔑 Using key: ${cosmosKey.take(10)}..."

        // First, test basic connectivity
        println "\n   🔍 Step 1: Testing basic network connectivity..."
        try {
            cosmosNoSqlUtils.testConnectivity()
            println "   ✅ Network connectivity verified"
        } catch (Exception connectEx) {
            println "   ⚠️ Network connectivity test failed (non-fatal at this stage)"
            println "      Error: ${connectEx.message}"
        }

        while (retryCount < maxRetries) {
            try {
                println "   📡 Checking Cosmos DB connectivity (attempt ${retryCount + 1}/$maxRetries)..."

                // Verify endpoint format
                if (!cosmosEndpoint.startsWith("https://")) {
                    throw new RuntimeException("Invalid endpoint format: ${cosmosEndpoint} - must start with https://")
                }

                // Check if port is valid
                def endpointUrl = new URL(cosmosEndpoint)
                def port = endpointUrl.port
                if (port == -1) {
                    println "   ⚠️ Warning: Port not explicitly set in endpoint, using HTTPS default (443)"
                } else {
                    println "   ℹ️ Port from endpoint: ${port}"
                }

                // Try to create a test client
                def testClient = cosmosNoSqlUtils.createClient()
                testClient.readAccountProperties()

                println "✅ Cosmos DB is ready: ${cosmosEndpoint}"
                return  // Success - exit method
            } catch (Exception e) {
                retryCount++
                println "   ⏳ Cosmos DB not ready yet"
                println "      Error: ${e.class.simpleName}: ${e.message}"

                if (retryCount < maxRetries) {
                    println "      Retry attempt ${retryCount}/$maxRetries..."
                    Thread.sleep(2000)  // Wait 2 seconds between retries
                } else {
                    println "❌ Cosmos DB failed to become ready after $maxRetries attempts"
                    println "❌ Last error: ${e.class.simpleName}: ${e.message}"
                    println "\n📋 DEBUGGING INFORMATION:"
                    println "   Endpoint: ${cosmosEndpoint}"
                    println "   Container Status: ${cosmosContainer.isRunning()}"
                    println "   Container Logs:"
                    try {
                        println cosmosContainer.getLogs()
                    } catch (Exception logE) {
                        println "   (Could not retrieve logs: ${logE.message})"
                    }
                    throw new RuntimeException("Cosmos DB not ready after $maxRetries attempts: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Cleanup after tests - automatically stops Cosmos DB container
     */
    def cleanupSpec() {
        println "\n=== cleanupSpec for: ${getClass().simpleName} ==="
        try {
            if (cosmosNoSqlUtils != null) {
                println "🛑 Closing Cosmos NoSQL DB client..."
                cosmosNoSqlUtils.closeClient()
                println "✅ Cosmos NoSQL DB client closed"
            }
            if (cosmosContainer != null) {
                println "🛑 Stopping Cosmos DB Emulator container..."
                cosmosContainer.stop()
                println "✅ Cosmos DB Emulator container stopped"
            }
            if (cosmosNetwork != null) {
                println "🛑 Closing Cosmos DB network..."
                cosmosNetwork.close()
                println "✅ Cosmos DB network closed"
            }
            println "✅ All containers and resources cleaned up successfully"
        } catch (Exception e) {
            println "❌ Error during cleanup: ${e.message}"
            e.printStackTrace()
        }
        println "==============================================\n"
    }

    /**
     * Dynamically inject Cosmos DB properties into Spring context
     * This method is called by Spring BEFORE the application context is created
     */
    @DynamicPropertySource
    static void registerCosmosProperties(DynamicPropertyRegistry registry) {
        println "\n=== @DynamicPropertySource called by Spring ==="

        if (cosmosEndpoint == null) {
            println "❌ ERROR: Cosmos endpoint is NULL!"
            throw new IllegalStateException("Cosmos endpoint is null - static initialization failed!")
        }

        if (cosmosKey == null) {
            println "❌ ERROR: Cosmos key is NULL!"
            throw new IllegalStateException("Cosmos key is null - static initialization failed!")
        }

        println "✅ Registering Cosmos NoSQL DB properties with Spring"
        println "   - Cosmos endpoint: ${cosmosEndpoint}"
        println "   - Using Testcontainers-managed Cosmos Emulator"

        // Cosmos DB properties
        registry.add("spring.cloud.azure.cosmos.endpoint") { cosmosEndpoint }
        registry.add("spring.cloud.azure.cosmos.key") { cosmosKey }
        registry.add("spring.cloud.azure.cosmos.database") { "testdb" }
        registry.add("azure.cosmos.uri") { cosmosEndpoint }
        registry.add("azure.cosmos.key") { cosmosKey }

        println "✅ Cosmos NoSQL DB properties registered successfully"
    }

    /**
     * Test connectivity to the endpoint
     */
    private static void testConnectionToEndpoint(String endpoint) {
        try {
            println "   🔍 Attempting to connect to: ${endpoint}"
            def url = new URL(endpoint)

            // Extract host and port
            def host = url.host
            def port = url.port == -1 ? 8081 : url.port

            println "   📍 Parsed: host=${host}, port=${port}"

            // Try socket connection first (quick check) with detailed diagnostics
            println "   🔗 Testing socket connection to ${host}:${port}..."
            def socket = new Socket()
            try {
                socket.connect(new java.net.InetSocketAddress(host, port), 10000)
                println "   ✅ Socket connection successful to ${host}:${port}"
                socket.close()
            } catch (Exception se) {
                println "   ❌ Socket connection failed: ${se.class.simpleName}: ${se.message}"
                println "      Endpoint: ${endpoint}"
                println "      Host: ${host}"
                println "      Port: ${port}"
                println "      This may indicate:"
                println "      - Container not listening on port ${port}"
                println "      - Firewall blocking connection"
                println "      - Port mapping not correctly established"
                println "      - Container may have crashed during startup"
                try {
                    socket.close()
                } catch (Exception ignored) {}
                throw se
            }

            // Try HTTPS connection
            println "   🔒 Testing HTTPS connection..."
            def conn = (HttpsURLConnection) url.openConnection()
            conn.setConnectTimeout(10000)
            conn.setReadTimeout(10000)
            conn.setRequestMethod("GET")

            def responseCode = conn.getResponseCode()
            println "   ✅ HTTPS response code: ${responseCode}"

            println "✅ Endpoint is reachable: ${endpoint}"
        } catch (ConnectException e) {
            println "   ❌ Connection refused: ${e.message}"
            println "      This usually means the container is not listening on the port"
            println "      or there's a firewall blocking the connection"
            println "      Endpoint: ${endpoint}"
            throw e
        } catch (SocketTimeoutException e) {
            println "   ❌ Connection timeout: ${e.message}"
            println "      The endpoint is not responding within 10 seconds"
            println "      Endpoint: ${endpoint}"
            throw e
        } catch (Exception e) {
            println "   ⚠️ Warning: Could not verify connectivity: ${e.class.simpleName}: ${e.message}"
            println "      Endpoint: ${endpoint}"
            // Don't throw - might be expected for some connection scenarios
        }
    }
}
