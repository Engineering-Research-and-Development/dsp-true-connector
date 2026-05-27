package it.eng.datatransfer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TransportProfileResolverTest {

    private final TransportProfileResolver resolver = new TransportProfileResolver();

    @Test
    @DisplayName("resolve returns STREAM_GRPC profile for stream:grpc transfer type")
    public void resolveReturnsGrpcForGrpcTransferType() {
        String profile = resolver.resolve("stream:grpc");
        assertEquals("stream:grpc", profile);
    }

    @Test
    @DisplayName("resolve returns STREAM_KAFKA profile for stream:kafka transfer type")
    public void resolveReturnsKafkaForKafkaTransferType() {
        String profile = resolver.resolve("stream:kafka");
        assertEquals("stream:kafka", profile);
    }

    @Test
    @DisplayName("resolve returns null for HttpData-PULL transfer type")
    public void resolveReturnsNullForHttpPull() {
        assertNull(resolver.resolve("HttpData-PULL"));
    }

    @Test
    @DisplayName("resolve returns null for HttpData-PUSH transfer type")
    public void resolveReturnsNullForHttpPush() {
        assertNull(resolver.resolve("HttpData-PUSH"));
    }

    @Test
    @DisplayName("resolve returns null for null input")
    public void resolveReturnsNullForNull() {
        assertNull(resolver.resolve(null));
    }

    @Test
    @DisplayName("resolve returns null for unknown transfer type")
    public void resolveReturnsNullForUnknownType() {
        assertNull(resolver.resolve("Unknown-TYPE"));
    }
}
