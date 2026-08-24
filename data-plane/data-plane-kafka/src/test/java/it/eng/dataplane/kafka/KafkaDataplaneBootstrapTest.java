package it.eng.dataplane.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Smoke tests for the Kafka dataplane module bootstrap classes.
 */
class KafkaDataplaneBootstrapTest {

    @Test
    @DisplayName("Kafka dataplane application class exists")
    void kafkaDataplaneApplicationClassExists() {
        assertDoesNotThrow(() -> Class.forName("it.eng.dataplane.kafka.DataPlaneKafkaApplication"));
    }

    @Test
    @DisplayName("Kafka stream transfer protocol class exists")
    void kafkaStreamTransferProtocolClassExists() {
        assertDoesNotThrow(() -> Class.forName("it.eng.dataplane.kafka.KafkaStreamTransferProtocol"));
    }
}
