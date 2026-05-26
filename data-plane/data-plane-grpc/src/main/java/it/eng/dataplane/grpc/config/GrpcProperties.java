package it.eng.dataplane.grpc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the gRPC streaming server.
 *
 * <p>Bound from the {@code grpc.server.*} prefix. The host and port values are embedded into
 * the {@code dataAddress} returned by the DPS {@code prepare} response so consumers know
 * where to connect.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "grpc.server")
public class GrpcProperties {

    /** Host or IP address advertised to consumers in the prepare response. */
    private String host = "localhost";

    /** Port the gRPC server listens on. */
    private int port = 9094;
}
