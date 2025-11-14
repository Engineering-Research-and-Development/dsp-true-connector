package it.eng.tools.client.rest;

import it.eng.tools.filter.CorrelationIdFilter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * OkHttp interceptor that propagates the correlation ID from MDC to outbound HTTP requests.
 * This ensures that the correlation ID is maintained across service boundaries for
 * distributed tracing and debugging.
 */
@Slf4j
public class CorrelationIdInterceptor implements Interceptor {

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // Get correlation ID from MDC
        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();

        if (correlationId != null && !correlationId.isBlank()) {
            // Add correlation ID header to the outbound request
            Request requestWithCorrelationId = originalRequest.newBuilder()
                    .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                    .build();

            log.debug("Propagating correlation ID {} to {}", correlationId, originalRequest.url());

            return chain.proceed(requestWithCorrelationId);
        } else {
            log.debug("No correlation ID in MDC for request to {}", originalRequest.url());
            return chain.proceed(originalRequest);
        }
    }
}

