package it.eng.dataplane.api.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SinkContext}.
 */
class SinkContextTest {

    @Test
    @DisplayName("build() with properties stores and returns them via getProperty()")
    void build_withProperties_storesAndReturnsValues() {
        SinkContext ctx = SinkContext.Builder.newInstance()
                .properties(Map.of("bucketName", "sink-bucket", "objectKey", "sink-key"))
                .build();

        assertThat(ctx.getProperty("bucketName")).isEqualTo("sink-bucket");
        assertThat(ctx.getProperty("objectKey")).isEqualTo("sink-key");
    }

    @Test
    @DisplayName("getProperty() returns null for absent key")
    void getProperty_withAbsentKey_returnsNull() {
        SinkContext ctx = SinkContext.Builder.newInstance()
                .properties(Map.of("bucketName", "sink-bucket"))
                .build();

        assertThat(ctx.getProperty("missing")).isNull();
    }

    @Test
    @DisplayName("build() without properties defaults to empty map")
    void build_withoutProperties_defaultsToEmptyMap() {
        SinkContext ctx = SinkContext.Builder.newInstance().build();

        assertThat(ctx.getProperty("any")).isNull();
        assertThat(ctx.getProperties()).isEmpty();
    }

    @Test
    @DisplayName("properties(null) is treated as empty map")
    void properties_withNull_treatedAsEmptyMap() {
        SinkContext ctx = SinkContext.Builder.newInstance()
                .properties(null)
                .build();

        assertThat(ctx.getProperties()).isEmpty();
        assertThat(ctx.getProperty("any")).isNull();
    }

    @Test
    @DisplayName("built context is immutable — mutations to source map do not affect context")
    void build_copiesMap_sourceMapMutationDoesNotAffectContext() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("key1", "val1");
        SinkContext ctx = SinkContext.Builder.newInstance()
                .properties(mutable)
                .build();

        mutable.put("key1", "changed");

        assertThat(ctx.getProperty("key1")).isEqualTo("val1");
    }

    @Test
    @DisplayName("returned properties map is unmodifiable")
    void getProperties_returnsUnmodifiableMap() {
        SinkContext ctx = SinkContext.Builder.newInstance()
                .properties(Map.of("k", "v"))
                .build();

        assertThatThrownBy(() -> ctx.getProperties().put("k", "new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
