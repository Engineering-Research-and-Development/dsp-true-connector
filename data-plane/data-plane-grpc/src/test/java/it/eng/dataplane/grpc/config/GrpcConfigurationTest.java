package it.eng.dataplane.grpc.config;

import io.grpc.Server;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.dataplane.grpc.registry.GrpcSessionRegistry;
import it.eng.dataplane.grpc.server.DataStreamService;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GrpcConfiguration}.
 */
@ExtendWith(MockitoExtension.class)
class GrpcConfigurationTest {

    @Mock
    private GrpcSessionRegistry sessionRegistry;
    @Mock
    private SourceReaderRegistry sourceReaderRegistry;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private GrpcProperties grpcProperties;

    private DataStreamService dataStreamService;
    private GrpcConfiguration configuration;

    @BeforeEach
    void setUp() {
        dataStreamService = new DataStreamService(sessionRegistry, sourceReaderRegistry, s3Properties);
        configuration = new GrpcConfiguration();
    }

    @Test
    @DisplayName("grpcServer() does not register a JVM shutdown hook")
    void grpcServer_doesNotRegisterJvmShutdownHook() throws Exception {
        Mockito.when(grpcProperties.getPort()).thenReturn(0);

        int hooksBefore = countShutdownHooks();
        Server server = configuration.grpcServer(dataStreamService, grpcProperties);
        int hooksAfter = countShutdownHooks();

        server.shutdownNow();

        assertThat(hooksAfter)
                .as("grpcServer() must not register any JVM shutdown hook")
                .isEqualTo(hooksBefore);
    }

    @Test
    @DisplayName("grpcServer() starts the server and returns a running instance")
    void grpcServer_startsServer_returnsRunningInstance() throws Exception {
        Mockito.when(grpcProperties.getPort()).thenReturn(0);

        Server server = configuration.grpcServer(dataStreamService, grpcProperties);

        try {
            assertThat(server.isShutdown()).isFalse();
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("transferExecutor() returns a non-null executor")
    void transferExecutor_returnsNonNullExecutor() {
        Executor executor = configuration.transferExecutor();

        assertThat(executor).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static int countShutdownHooks() throws Exception {
        Class<?> clazz = Class.forName("java.lang.ApplicationShutdownHooks");
        Field field = clazz.getDeclaredField("hooks");
        field.setAccessible(true);
        synchronized (clazz) {
            Map<?, ?> hooks = (Map<?, ?>) field.get(null);
            return hooks.size();
        }
    }
}
