package it.eng.dataplane.grpc.client;

import io.grpc.ManagedChannel;

/**
 * Factory for creating gRPC channels to remote providers.
 */
@FunctionalInterface
public interface GrpcChannelFactory {

    /**
     * Creates a new managed channel for the given host and port.
     *
     * @param host remote host name or IP
     * @param port remote port
     * @return managed channel instance
     */
    ManagedChannel create(String host, int port);
}
