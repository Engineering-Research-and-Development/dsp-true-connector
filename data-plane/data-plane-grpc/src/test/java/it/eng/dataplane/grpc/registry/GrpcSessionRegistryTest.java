package it.eng.dataplane.grpc.registry;

import it.eng.dataplane.grpc.model.GrpcSessionState;
import it.eng.dataplane.grpc.model.GrpcStreamSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GrpcSessionRegistry}.
 */
class GrpcSessionRegistryTest {

    private GrpcSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GrpcSessionRegistry();
    }

    @Test
    @DisplayName("register() and findByProcessId() returns the registered session")
    void register_andFindByProcessId_returnsSession() {
        GrpcStreamSession session = buildSession("session-1", "process-1", true);
        registry.register(session);

        Optional<GrpcStreamSession> found = registry.findByProcessId("process-1");

        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo("session-1");
        assertThat(found.get().getProcessId()).isEqualTo("process-1");
    }

    @Test
    @DisplayName("register() and findBySessionId() returns the registered session")
    void register_andFindBySessionId_returnsSession() {
        GrpcStreamSession session = buildSession("session-2", "process-2", false);
        registry.register(session);

        Optional<GrpcStreamSession> found = registry.findBySessionId("session-2");

        assertThat(found).isPresent();
        assertThat(found.get().getProcessId()).isEqualTo("process-2");
        assertThat(found.get().isFinite()).isFalse();
    }

    @Test
    @DisplayName("removeByProcessId() removes session from both indexes")
    void removeByProcessId_removesSessionFromBothIndexes() {
        GrpcStreamSession session = buildSession("session-3", "process-3", true);
        registry.register(session);

        registry.removeByProcessId("process-3");

        assertThat(registry.findByProcessId("process-3")).isEmpty();
        assertThat(registry.findBySessionId("session-3")).isEmpty();
    }

    @Test
    @DisplayName("findByProcessId() returns empty when processId is not registered")
    void findByProcessId_nonExistent_returnsEmpty() {
        Optional<GrpcStreamSession> found = registry.findByProcessId("does-not-exist");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findBySessionId() returns empty when sessionId is not registered")
    void findBySessionId_nonExistent_returnsEmpty() {
        Optional<GrpcStreamSession> found = registry.findBySessionId("does-not-exist");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("removeByProcessId() is a no-op for unknown processId")
    void removeByProcessId_unknownProcessId_isNoOp() {
        // Must not throw
        registry.removeByProcessId("unknown-process");

        assertThat(registry.findByProcessId("unknown-process")).isEmpty();
    }

    @Test
    @DisplayName("register() with same processId evicts the prior session from bySessionId")
    void register_sameProcessId_evictsPriorSessionFromBySessionId() {
        GrpcStreamSession firstSession = buildSession("session-old", "process-retry", true);
        registry.register(firstSession);

        GrpcStreamSession secondSession = buildSession("session-new", "process-retry", true);
        registry.register(secondSession);

        // The old sessionId entry must be gone from the bySessionId index.
        assertThat(registry.findBySessionId("session-old")).isEmpty();
        // The new session must be reachable by both indexes.
        assertThat(registry.findBySessionId("session-new")).isPresent();
        assertThat(registry.findByProcessId("process-retry"))
                .isPresent()
                .hasValueSatisfying(s -> assertThat(s.getSessionId()).isEqualTo("session-new"));
        // Only one session total — no leaked entry.
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("size() returns number of registered sessions")
    void size_reflectsRegisteredCount() {
        assertThat(registry.size()).isZero();

        registry.register(buildSession("s-a", "p-a", true));
        registry.register(buildSession("s-b", "p-b", false));

        assertThat(registry.size()).isEqualTo(2);

        registry.removeByProcessId("p-a");

        assertThat(registry.size()).isEqualTo(1);
    }

    private GrpcStreamSession buildSession(String sessionId, String processId, boolean finite) {
        return GrpcStreamSession.Builder.newInstance()
                .sessionId(sessionId)
                .processId(processId)
                .datasetId("ds-" + processId)
                .finite(finite)
                .state(GrpcSessionState.PREPARED)
                .build();
    }
}
