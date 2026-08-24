package it.eng.dataplane.grpc.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import it.eng.dataplane.grpc.server.DataStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for the gRPC Data Plane module.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GrpcProperties.class)
public class GrpcConfiguration {

    /**
     * Creates the transfer executor used by asynchronous gRPC consumer pulls.
     *
     * @return virtual-thread-per-task executor
     */
    @Bean("transferExecutor")
    public Executor transferExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Starts the provider-side gRPC server.
     *
     * <p>Shutdown is managed exclusively via Spring's bean destroy method ({@code shutdownNow}).
     * No manual JVM shutdown hook is registered; Spring's own shutdown hook closes the
     * application context, which in turn calls the destroy method.</p>
     *
     * @param dataStreamService data-stream service implementation
     * @param grpcProperties gRPC server properties
     * @return started gRPC server
     * @throws IOException if the server cannot be started
     */
    @Bean(destroyMethod = "shutdownNow")
    public Server grpcServer(DataStreamService dataStreamService, GrpcProperties grpcProperties) throws IOException {
        Server server = ServerBuilder.forPort(grpcProperties.getPort())
                .addService(dataStreamService)
                .build()
                .start();
        log.info("gRPC server started on port {}", grpcProperties.getPort());
        return server;
    }
}
