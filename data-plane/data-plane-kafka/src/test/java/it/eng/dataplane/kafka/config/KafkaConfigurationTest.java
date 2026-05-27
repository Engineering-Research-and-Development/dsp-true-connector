package it.eng.dataplane.kafka.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaConfiguration}.
 */
class KafkaConfigurationTest {

    @Test
    @DisplayName("transferExecutor() returns a non-null executor")
    void transferExecutor_returnsNonNullExecutor() {
        KafkaConfiguration configuration = new KafkaConfiguration();

        Executor executor = configuration.transferExecutor();

        assertThat(executor).isNotNull();
    }
}
