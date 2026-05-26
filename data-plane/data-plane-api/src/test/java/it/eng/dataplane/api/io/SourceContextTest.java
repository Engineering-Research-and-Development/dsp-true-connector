package it.eng.dataplane.api.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SourceContext}.
 */
class SourceContextTest {

    @Test
    @DisplayName("build() with properties stores and returns them via getProperty()")
    void build_withProperties_storesAndReturnsValues() {
        SourceContext ctx = SourceContext.Builder.newInstance()
                .properties(Map.of("bucketName", "my-bucket", "objectKey", "my-key"))
                .build();

        assertThat(ctx.getProperty("bucketName")).isEqualTo("my-bucket");
        assertThat(ctx.getProperty("objectKey")).isEqualTo("my-key");
    }

    @Test
    @DisplayName("getProperty() returns null for absent key")
    void getProperty_withAbsentKey_returnsNull() {
        SourceContext ctx = SourceContext.Builder.newInstance()
                .properties(Map.of("bucketName", "my-bucket"))
                .build();

        assertThat(ctx.getProperty("missing")).isNull();
    }

    @Test
    @DisplayName("build() without properties defaults to empty map")
    void build_withoutProperties_defaultsToEmptyMap() {
        SourceContext ctx = SourceContext.Builder.newInstance().build();

        assertThat(ctx.getProperty("any")).isNull();
        assertThat(ctx.getProperties()).isEmpty();
    }

    @Test
    @DisplayName("properties(null) is treated as empty map")
    void properties_withNull_treatedAsEmptyMap() {
        SourceContext ctx = SourceContext.Builder.newInstance()
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
        SourceContext ctx = SourceContext.Builder.newInstance()
                .properties(mutable)
                .build();

        mutable.put("key1", "changed");

        assertThat(ctx.getProperty("key1")).isEqualTo("val1");
    }

    @Test
    @DisplayName("returned properties map is unmodifiable")
    void getProperties_returnsUnmodifiableMap() {
        SourceContext ctx = SourceContext.Builder.newInstance()
                .properties(Map.of("k", "v"))
                .build();

        assertThatThrownBy(() -> ctx.getProperties().put("k", "new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
