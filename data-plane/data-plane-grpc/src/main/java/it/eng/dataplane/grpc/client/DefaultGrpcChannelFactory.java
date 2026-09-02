package it.eng.dataplane.grpc.client;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.stereotype.Component;

/**
 * Default {@link GrpcChannelFactory} backed by Netty.
 */
@Component
public class DefaultGrpcChannelFactory implements GrpcChannelFactory {

    /**
     * Creates a plaintext Netty channel to the given host and port.
     *
     * @param host remote host name or IP
     * @param port remote port
     * @return managed channel instance
     */
    @Override
    public ManagedChannel create(String host, int port) {
        return NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }
}
