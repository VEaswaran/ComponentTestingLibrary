# Zookeeper Connection Issue - Fixed

## Problem
Zookeeper was closing connections from Kafka with error:
```
INFO Unable to read additional data from client, it probably closed the socket: address = /172.19.0.1:60056, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
```

This indicated that either:
1. Kafka was unable to properly connect to Zookeeper
2. Zookeeper was terminating connections prematurely
3. Network communication between containers was disrupted

## Root Causes
1. **Missing KAFKA_LISTENER_SECURITY_PROTOCOL_MAP**: Critical configuration for listener protocol mapping
2. **Missing KAFKA_INTER_BROKER_LISTENER_NAME**: Kafka couldn't establish internal broker communication
3. **Socket connection testing interfering with startup**: Unnecessary socket operations were opening/closing connections to Zookeeper during initialization
4. **Insufficient initialization time**: Kafka wasn't being given enough time to stabilize its Zookeeper connection

## Solutions Implemented

### 1. **Added Missing Kafka Listener Configuration** ⭐ CRITICAL
```groovy
.withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT")
.withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT_INTERNAL")
```

This configuration is **essential** for Kafka to:
- Map listener names to security protocols
- Communicate with other brokers internally
- Accept external client connections

### 2. **Removed Unnecessary Socket Testing**
Removed direct socket connection attempts to Zookeeper that were:
- Opening connections during initialization
- Interfering with Kafka's own connection establishment
- Causing socket resets that Zookeeper logged

### 3. **Fixed DescribeClusterResult API Call**
Changed from incorrect:
```groovy
def clusterDescription = clusterDescriptionFuture.get(10, TimeUnit.SECONDS)
```

To correct:
```groovy
def clusterDescription = clusterDescriptionResult.all().get(10, TimeUnit.SECONDS)
```

### 4. **Simplified Zookeeper Verification**
- Removed aggressive socket testing
- Relies on Kafka's built-in connection retry logic
- Reduces noise in logs from connection attempts

### 5. **Proper Wait Timings**
- Zookeeper: 15 seconds to fully initialize
- Kafka: 20 seconds to fully initialize and establish Zookeeper connection
- Broker readiness: Up to 50 retries with 2-second intervals = 100 seconds maximum

## Kafka Configuration Summary

### Critical Listener Settings
```groovy
KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:9092,PLAINTEXT_INTERNAL://kafka:29092"
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT"
KAFKA_INTER_BROKER_LISTENER_NAME: "PLAINTEXT_INTERNAL"
```

### Network Communication
- **PLAINTEXT://kafka:9092**: External clients connect here (accessible outside Docker network)
- **PLAINTEXT_INTERNAL://kafka:29092**: Brokers communicate with each other internally
- Both use PLAINTEXT (no SSL/TLS) for testing environment

### Full Configuration
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
KAFKA_JVM_PERFORMANCE_OPTS: "-Xms256m -Xmx512m"
KAFKA_NUM_NETWORK_THREADS: "8"
KAFKA_NUM_IO_THREADS: "8"
```

## What Now Works
✅ Zookeeper starts and remains running  
✅ Kafka properly connects to Zookeeper  
✅ Kafka advertises itself on correct listeners  
✅ No premature socket connection closures  
✅ Kafka broker becomes ready for connections  
✅ Integration tests can produce/consume messages  
✅ Containers properly initialize without interference  

## Testing
Run any integration test that extends `IntegrationTestBaseSpec`:
```bash
mvn clean test
```

Expected log output indicators:
- `✅ Zookeeper container started`
- `✅ Kafka container is running`
- `✅ Kafka container still running after wait`
- `✅ Kafka broker ready: 1 broker(s) available`
- `✅ Ready to run tests`

## Key Takeaway
The `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` is **NOT optional** - it's a critical configuration that maps protocol names to security protocols. Without it, Kafka crashes because it can't find the protocol definition for the listeners it's trying to bind to.

