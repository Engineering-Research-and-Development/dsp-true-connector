package it.eng.dataplane.core.registry;

import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.io.SourceReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SourceReaderRegistry}.
 */
@ExtendWith(MockitoExtension.class)
class SourceReaderRegistryTest {

    @Mock
    private SourceReader s3SourceReader;

    private SourceReaderRegistry registry;

    @BeforeEach
    void setUp() {
        when(s3SourceReader.getSourceType()).thenReturn("s3");
        registry = new SourceReaderRegistry(List.of(s3SourceReader));
    }

    @Test
    @DisplayName("getReader returns matching reader for a known source type")
    void getReader_withKnownType_returnsReader() {
        assertThat(registry.getReader("s3"))
                .contains(s3SourceReader);
    }

    @Test
    @DisplayName("getReader returns empty for an unknown source type")
    void getReader_withUnknownType_returnsEmpty() {
        assertThat(registry.getReader("unknown"))
                .isEmpty();
    }

    @Test
    @DisplayName("getReader returns empty when the registry has no readers")
    void getReader_withEmptyRegistry_returnsEmpty() {
        SourceReaderRegistry emptyRegistry = new SourceReaderRegistry(List.of());

        assertThat(emptyRegistry.getReader("s3"))
                .isEmpty();
    }

    @Test
    @DisplayName("constructor throws with actionable message when two readers share the same source type")
    void constructor_withDuplicateType_throwsWithDiagnostics() {
        SourceReader duplicate = new SourceReader() {
            @Override
            public String getSourceType() {
                return "s3";
            }

            @Override
            public SourceOpenResult open(SourceContext context) {
                return SourceOpenResult.failure("not used");
            }
        };

        assertThatThrownBy(() -> new SourceReaderRegistry(List.of(s3SourceReader, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s3")
                .hasMessageContaining(s3SourceReader.getClass().getName());
    }
}
