package it.eng.dataplane.api.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SinkWriteResult}.
 */
class SinkWriteResultTest {

    @Test
    @DisplayName("success() sets success flag and objectIdentifier")
    void success_setsSuccessTrueAndObjectIdentifier() {
        SinkWriteResult result = SinkWriteResult.success("etag-abc123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getObjectIdentifier()).isEqualTo("etag-abc123");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("success() accepts null objectIdentifier")
    void success_withNullObjectIdentifier_isAccepted() {
        SinkWriteResult result = SinkWriteResult.success(null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getObjectIdentifier()).isNull();
    }

    @Test
    @DisplayName("failure() sets success to false and stores errorMessage")
    void failure_setsSuccessFalseAndErrorMessage() {
        SinkWriteResult result = SinkWriteResult.failure("upload timed out");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("upload timed out");
        assertThat(result.getObjectIdentifier()).isNull();
    }

    @Test
    @DisplayName("failure() and success() are independent instances")
    void success_andFailure_returnIndependentInstances() {
        SinkWriteResult success = SinkWriteResult.success("etag");
        SinkWriteResult failure = SinkWriteResult.failure("error");

        assertThat(success).isNotSameAs(failure);
        assertThat(success.isSuccess()).isTrue();
        assertThat(failure.isSuccess()).isFalse();
    }
}
