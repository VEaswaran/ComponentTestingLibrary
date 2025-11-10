# Kafka Container Shutdown Issue - Fixed

## Problem
Kafka container was starting and immediately stopping. Issues included:
- Container crashing with no clear error messages
- Missing `KAFKA_ADVERTISED_LISTENERS` configuration
- Missing listener security protocol mapping
- Insufficient container health checks
- No startup logs to diagnose failures

## Root Causes
1. **Missing ADVERTISED_LISTENERS**: Kafka couldn't advertise itself to clients
2. **No LISTENER_SECURITY_PROTOCOL_MAP**: Protocol mapping was undefined
3. **Missing INTER_BROKER_LISTENER_NAME**: Kafka brokers couldn't communicate internally
4. **No startup verification**: Container state wasn't verified before tests ran

## Solutions Implemented

### 1. **Added Required Kafka Environment Variables**
```groovy
.withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092,PLAINTEXT_INTERNAL://kafka:29092")
.withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT")
.withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT_INTERNAL")
```

### 2. **Enhanced Container Lifecycle Management**
- Added explicit container running checks after startup
- Capture and display Kafka logs if container stops
- Wait longer for Kafka to fully initialize (20 seconds)
- Verify Zookeeper connectivity before checking Kafka

### 3. **Improved Error Logging**
- If Kafka container is not running, logs are captured and displayed
- Detailed container status output
- Clear error messages for debugging

### 4. **Added Network Verification**
- Test Zookeeper socket connection before starting Kafka
- Ensure Zookeeper is fully initialized (10-second wait)
- Network connectivity checks with timeout

### 5. **Fixed DescribeCluster() Call**
Changed from:
```groovy
def clusterDescription = clusterDescriptionFuture.get(10, TimeUnit.SECONDS)
```

To:
```groovy
def clusterDescription = clusterDescriptionResult.all().get(10, TimeUnit.SECONDS)
```

The `DescribeClusterResult` doesn't have a direct `get()` method - you must call `.all()` first to get the `CompletableFuture`.

## Key Configuration Changes

### Kafka Environment Variables
```groovy
KAFKA_BROKER_ID: "1"
KAFKA_ZOOKEEPER_CONNECT: "zookeeper:2181"
KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:9092,PLAINTEXT_INTERNAL://kafka:29092"
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT"
KAFKA_INTER_BROKER_LISTENER_NAME: "PLAINTEXT_INTERNAL"
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: "0"
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: "1"
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: "1"
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"
```

### Network Configuration
- Both Kafka and Zookeeper on same Docker network: `kafka-network`
- Zookeeper aliases: `zookeeper:2181` (internal network)
- Kafka aliases: `kafka:9092` (internal network)
- Proper listener configuration for both internal and external communication

## Wait Times
- Zookeeper startup + initialization: 10 seconds
- Kafka startup + initialization: 20 seconds
- Zookeeper connectivity check: up to 10 retries × 1 second
- Kafka broker readiness: up to 50 retries × 2 seconds

## Testing
The fix ensures:
1. ✅ Kafka container starts and remains running
2. ✅ Zookeeper is reachable before tests begin
3. ✅ Kafka broker is fully initialized and ready
4. ✅ Integration tests can connect and produce/consume messages
5. ✅ Containers are properly cleaned up after tests

## How to Test
Run any integration test that extends `IntegrationTestBaseSpec`:
```bash
mvn clean test
```

Watch for these success indicators in logs:
- `✅ Kafka container is running`
- `✅ Kafka container still running after wait`
- `✅ Kafka broker ready: X broker(s) available`
- `✅ Ready to run tests`

