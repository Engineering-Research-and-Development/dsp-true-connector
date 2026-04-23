package it.eng.datatransfer.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registry of per-transfer cancellation tokens.
 *
 * <p>Each active download registers a token before starting. The suspend path calls
 * {@link #signal} to set the token to {@code true}, causing the running upload loop
 * to stop gracefully after its current chunk.
 */
@Component
public class CancellationRegistry {

    private final ConcurrentHashMap<String, AtomicBoolean> tokens = new ConcurrentHashMap<>();

    /**
     * Registers a new cancellation token for the given transfer process ID.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     * @return the new token, initially {@code false}
     */
    public AtomicBoolean register(String transferProcessId) {
        AtomicBoolean token = new AtomicBoolean(false);
        tokens.put(transferProcessId, token);
        return token;
    }

    /**
     * Sets the cancellation token for the given transfer process to {@code true}.
     * No-op if no token is currently registered.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     */
    public void signal(String transferProcessId) {
        AtomicBoolean token = tokens.get(transferProcessId);
        if (token != null) {
            token.set(true);
        }
    }

    /**
     * Removes the cancellation token for the given transfer process.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     */
    public void deregister(String transferProcessId) {
        tokens.remove(transferProcessId);
    }

    /**
     * Returns {@code true} if a token is currently registered for the given ID.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     * @return {@code true} if registered
     */
    public boolean isRegistered(String transferProcessId) {
        return tokens.containsKey(transferProcessId);
    }
}
