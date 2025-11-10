package com.demo.testing.utils

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

import java.time.Duration

/**
 * Utility class for Kafka testing operations.
 * Provides methods to consume and verify messages from Kafka topics.
 */
class KafkaTestUtils {

    private final String bootstrapServers

    KafkaTestUtils(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers
    }

    /**
     * Consume messages from a Kafka topic with configurable offset strategy.
     *
     * @param topic The Kafka topic to consume from
     * @param expectedCount Number of messages expected
     * @param timeout Maximum time to wait for messages
     * @param offsetReset "earliest" or "latest" - determines where to start reading
     * @return List of ConsumerRecords
     */
    List<ConsumerRecord<String, String>> consumeMessagesWithOffset(String topic, int expectedCount, Duration timeout, String offsetReset) {
        def groupId = "test-consumer-${UUID.randomUUID()}".toString()
        def props = [
            (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG): bootstrapServers,
            (ConsumerConfig.GROUP_ID_CONFIG): groupId,
            (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG): StringDeserializer.class.name,
            (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG): StringDeserializer.class.name,
            (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG): offsetReset,
            (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG): "false",
            (ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG): "30000"
        ]

        def consumer = new KafkaConsumer<String, String>(props)
        consumer.subscribe([topic])

        // Poll once to initialize the consumer and position it
        consumer.poll(Duration.ofMillis(100))

        def records = []
        def start = System.currentTimeMillis()
        def timeoutMillis = timeout.toMillis()

        try {
            while (records.size() < expectedCount && (System.currentTimeMillis() - start) < timeoutMillis) {
                def polled = consumer.poll(Duration.ofMillis(500))
                polled.each { record ->
                    records.add(record)
                }
            }
        } finally {
            consumer.close()
        }

        return records
    }

    /**
     * Consume messages from a Kafka topic within a timeout period.
     * This method will consume only NEW messages sent after the consumer is initialized,
     * not historical messages from the topic.
     *
     * @param topic The Kafka topic to consume from
     * @param expectedCount Number of messages expected
     * @param timeout Maximum time to wait for messages
     * @return List of ConsumerRecords
     */
    List<ConsumerRecord<String, String>> consumeMessages(String topic, int expectedCount, Duration timeout) {
        def groupId = "test-consumer-${UUID.randomUUID()}".toString()
        def props = [
            (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG): bootstrapServers,
            (ConsumerConfig.GROUP_ID_CONFIG): groupId,
            (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG): StringDeserializer.class.name,
            (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG): StringDeserializer.class.name,
            (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG): "latest",  // Start from latest offset
            (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG): "false",  // Manual commit to control behavior
            (ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG): "30000"
        ]

        def consumer = new KafkaConsumer<String, String>(props)
        consumer.subscribe([topic])

        // Poll once to initialize the consumer and position it
        consumer.poll(Duration.ofMillis(100))

        def records = []
        def start = System.currentTimeMillis()
        def timeoutMillis = timeout.toMillis()

        try {
            while (records.size() < expectedCount && (System.currentTimeMillis() - start) < timeoutMillis) {
                def polled = consumer.poll(Duration.ofMillis(500))
                polled.each { record ->
                    records.add(record)
                }
            }
        } finally {
            consumer.close()
        }

        return records
    }
}

