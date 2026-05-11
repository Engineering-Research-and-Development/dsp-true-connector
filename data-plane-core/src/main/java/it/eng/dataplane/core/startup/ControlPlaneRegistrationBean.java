package it.eng.dataplane.core.startup;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

/**
 * Registers this Data Plane with the Control Plane at startup.
 * Uses the shared OkHttpClient bean (TLS-aware).
 * Retry logic is added in Task 10; this stub logs failures without retry.
 */
@Slf4j
@Component
public class ControlPlaneRegistrationBean implements ApplicationListener<ApplicationReadyEvent> {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final DataPlaneProperties properties;
    private final DataTransferProtocolRegistry registry;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param properties DP runtime configuration
     * @param registry registered transfer protocol implementations
     * @param okHttpClient TLS-aware HTTP client from OkHttpClientConfiguration
     * @param objectMapper shared Jackson mapper
     */
    public ControlPlaneRegistrationBean(DataPlaneProperties properties,
                                        DataTransferProtocolRegistry registry,
                                        OkHttpClient okHttpClient,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (properties.getControlPlaneAdminEndpoint() == null) {
            log.warn("dataplane.control-plane-admin-endpoint not set, skipping CP registration");
            return;
        }
        String url = properties.getControlPlaneAdminEndpoint() + "/api/v1/dataplanes";
        try {
            Map<String, Object> payload = Map.of(
                "endpoint", properties.getEndpoint(),
                "supportedTransferTypes", registry.getSupportedProtocols()
            );
            String json = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                .url(url)
                .put(RequestBody.create(json, JSON))
                .addHeader("Content-Type", "application/json")
                .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Registered with Control Plane at {}", properties.getControlPlaneAdminEndpoint());
                } else {
                    log.error("CP registration rejected with HTTP {}", response.code());
                }
            }
        } catch (IOException e) {
            log.error("Failed to register with Control Plane: {}", e.getMessage());
        }
    }
}
