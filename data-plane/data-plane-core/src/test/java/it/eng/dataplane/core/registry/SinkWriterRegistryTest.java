package it.eng.dataplane.core.registry;

import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.dataplane.api.io.SinkWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SinkWriterRegistry}.
 */
@ExtendWith(MockitoExtension.class)
class SinkWriterRegistryTest {

    @Mock
    private SinkWriter s3SinkWriter;

    private SinkWriterRegistry registry;

    @BeforeEach
    void setUp() {
        when(s3SinkWriter.getSinkType()).thenReturn("s3");
        registry = new SinkWriterRegistry(List.of(s3SinkWriter));
    }

    @Test
    @DisplayName("getWriter returns matching writer for a known sink type")
    void getWriter_withKnownType_returnsWriter() {
        assertThat(registry.getWriter("s3"))
                .contains(s3SinkWriter);
    }

    @Test
    @DisplayName("getWriter returns empty for an unknown sink type")
    void getWriter_withUnknownType_returnsEmpty() {
        assertThat(registry.getWriter("unknown"))
                .isEmpty();
    }

    @Test
    @DisplayName("getWriter returns empty when the registry has no writers")
    void getWriter_withEmptyRegistry_returnsEmpty() {
        SinkWriterRegistry emptyRegistry = new SinkWriterRegistry(List.of());

        assertThat(emptyRegistry.getWriter("s3"))
                .isEmpty();
    }

    @Test
    @DisplayName("constructor throws with actionable message when two writers share the same sink type")
    void constructor_withDuplicateType_throwsWithDiagnostics() {
        SinkWriter duplicate = new SinkWriter() {
            @Override
            public String getSinkType() {
                return "s3";
            }

            @Override
            public SinkWriteResult write(InputStream data, SinkContext context) {
                return SinkWriteResult.failure("not used");
            }
        };

        assertThatThrownBy(() -> new SinkWriterRegistry(List.of(s3SinkWriter, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s3")
                .hasMessageContaining(s3SinkWriter.getClass().getName());
    }
}
