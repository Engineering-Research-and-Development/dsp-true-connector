package it.eng.dataplane.grpc.registry;

import it.eng.dataplane.grpc.model.GrpcStreamSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for active {@link GrpcStreamSession} instances.
 *
 * <p>Sessions are indexed by both {@code sessionId} and {@code processId} for efficient lookup.
 * All operations are thread-safe.</p>
 */
@Slf4j
@Component
public class GrpcSessionRegistry {

    private final ConcurrentHashMap<String, GrpcStreamSession> bySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> processToSessionId = new ConcurrentHashMap<>();

    /**
     * Registers a newly prepared session.
     *
     * <p>If a session is already registered for the same {@code processId} (e.g. a CP retry of
     * {@code /dataflows/prepare}), the prior entry is evicted from {@code bySessionId} before
     * the new session is stored, preventing orphaned session objects.</p>
     *
     * @param session the session to register
     */
    public void register(GrpcStreamSession session) {
        String priorSessionId = processToSessionId.put(session.getProcessId(), session.getSessionId());
        if (priorSessionId != null && !priorSessionId.equals(session.getSessionId())) {
            bySessionId.remove(priorSessionId);
            log.debug("Evicted prior gRPC session sessionId={} for processId={} (re-prepare)",
                    priorSessionId, session.getProcessId());
        }
        bySessionId.put(session.getSessionId(), session);
        log.debug("Registered gRPC session sessionId={} processId={}", session.getSessionId(), session.getProcessId());
    }

    /**
     * Returns the session for the given session identifier.
     *
     * @param sessionId session UUID
     * @return matching session, or empty if not found
     */
    public Optional<GrpcStreamSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(bySessionId.get(sessionId));
    }

    /**
     * Returns the session for the given transfer process identifier.
     *
     * @param processId transfer process ID
     * @return matching session, or empty if not found
     */
    public Optional<GrpcStreamSession> findByProcessId(String processId) {
        String sessionId = processToSessionId.get(processId);
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySessionId.get(sessionId));
    }

    /**
     * Removes the session associated with the given transfer process identifier.
     * No-op if the process has no registered session.
     *
     * @param processId transfer process ID
     */
    public void removeByProcessId(String processId) {
        String sessionId = processToSessionId.remove(processId);
        if (sessionId != null) {
            bySessionId.remove(sessionId);
            log.debug("Removed gRPC session sessionId={} processId={}", sessionId, processId);
        }
    }

    /**
     * Returns the number of currently registered sessions.
     *
     * @return registered session count
     */
    public int size() {
        return bySessionId.size();
    }
}
