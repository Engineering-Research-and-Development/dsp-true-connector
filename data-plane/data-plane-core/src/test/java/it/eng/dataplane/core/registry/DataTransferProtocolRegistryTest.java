package it.eng.dataplane.core.registry;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataTransferProtocolRegistry}.
 */
@ExtendWith(MockitoExtension.class)
class DataTransferProtocolRegistryTest {

    @Mock
    private DataTransferProtocol httpPullProtocol;

    @Mock
    private DataTransferProtocol httpPushProtocol;

    private DataTransferProtocolRegistry registry;

    @BeforeEach
    void setUp() {
        when(httpPullProtocol.getProtocolId()).thenReturn("HttpData-PULL");
        when(httpPushProtocol.getProtocolId()).thenReturn("HttpData-PUSH");
        
        registry = new DataTransferProtocolRegistry(List.of(httpPullProtocol, httpPushProtocol));
    }

    /**
     * Verifies that the registry can look up a protocol by its protocol ID.
     */
    @Test
    void registryLooksUpProtocolByProtocolId() {
        // When
        DataTransferProtocol result = registry.getProtocol("HttpData-PULL");

        // Then
        assertNotNull(result);
        assertEquals(httpPullProtocol, result);
        assertEquals("HttpData-PULL", result.getProtocolId());
    }

    /**
     * Verifies that the registry returns null for an unknown protocol ID.
     */
    @Test
    void registryReturnsNullForUnknownProtocol() {
        // When
        DataTransferProtocol result = registry.getProtocol("unknown-protocol");

        // Then
        assertNull(result);
    }

    /**
     * Verifies that getSupportedProtocols returns all registered protocol IDs.
     */
    @Test
    void getSupportedProtocolsReturnsAllKeys() {
        // When
        Set<String> supportedProtocols = registry.getSupportedProtocols();

        // Then
        assertNotNull(supportedProtocols);
        assertEquals(2, supportedProtocols.size());
        assertTrue(supportedProtocols.contains("HttpData-PULL"));
        assertTrue(supportedProtocols.contains("HttpData-PUSH"));
    }

    /**
     * Verifies that the set returned by getSupportedProtocols is immutable so that
     * callers cannot mutate the internal registry state through it.
     */
    @Test
    void getSupportedProtocolsReturnsImmutableSet() {
        // Given
        Set<String> supportedProtocols = registry.getSupportedProtocols();

        // Then – any attempt to mutate the returned set must throw
        assertThrows(UnsupportedOperationException.class, () -> supportedProtocols.add("rogue-protocol"));
        assertThrows(UnsupportedOperationException.class, () -> supportedProtocols.remove("HttpData-PULL"));

        // And – the registry itself is unaffected
        assertEquals(2, registry.getSupportedProtocols().size());
        assertTrue(registry.getSupportedProtocols().contains("HttpData-PULL"));
        assertTrue(registry.getSupportedProtocols().contains("HttpData-PUSH"));
    }
}
