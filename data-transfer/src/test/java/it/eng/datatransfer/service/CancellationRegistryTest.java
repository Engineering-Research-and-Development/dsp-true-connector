package it.eng.datatransfer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CancellationRegistryTest {

    private CancellationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CancellationRegistry();
    }

    @Test
    @DisplayName("register returns a false AtomicBoolean")
    void registerReturnsFalseToken() {
        AtomicBoolean token = registry.register("tp-1");
        assertFalse(token.get());
    }

    @Test
    @DisplayName("signal sets registered token to true")
    void signalSetsTokenTrue() {
        AtomicBoolean token = registry.register("tp-2");
        registry.signal("tp-2");
        assertTrue(token.get());
    }

    @Test
    @DisplayName("signal on unknown id is a no-op")
    void signalUnknownIdIsNoOp() {
        assertDoesNotThrow(() -> registry.signal("unknown-id"));
    }

    @Test
    @DisplayName("deregister removes the token")
    void deregisterRemovesToken() {
        registry.register("tp-3");
        registry.deregister("tp-3");
        assertFalse(registry.isRegistered("tp-3"));
    }

    @Test
    @DisplayName("signal after deregister is a no-op")
    void signalAfterDeregisterIsNoOp() {
        registry.register("tp-4");
        registry.deregister("tp-4");
        assertDoesNotThrow(() -> registry.signal("tp-4"));
    }

    @Test
    @DisplayName("isRegistered returns true only while token is present")
    void isRegisteredLifecycle() {
        assertFalse(registry.isRegistered("tp-5"));
        registry.register("tp-5");
        assertTrue(registry.isRegistered("tp-5"));
        registry.deregister("tp-5");
        assertFalse(registry.isRegistered("tp-5"));
    }

    @Test
    @DisplayName("register throws IllegalStateException if token already registered for the same id")
    void registerThrowsIfAlreadyRegistered() {
        registry.register("tp-dup");
        assertThrows(IllegalStateException.class, () -> registry.register("tp-dup"));
    }
}
