package it.eng.tools.auth.keycloak;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Runs one HTTP exchange with a deadline that covers the whole exchange, body included, and a cap on the
 * body size.
 * <p>
 * {@link HttpRequest.Builder#timeout(Duration)} only bounds the wait for the response headers. A server that
 * sends the headers and then stalls the body would block a plain {@code send} forever. This helper sends the
 * request asynchronously and waits for the complete, size-limited body at most {@code timeout}; on timeout
 * the exchange is cancelled. The body of a non-2xx response is discarded unread.
 */
public final class BoundedHttpExchange {

    private BoundedHttpExchange() {
    }

    /**
     * Status, headers and body of a completed exchange.
     *
     * @param statusCode the HTTP status code
     * @param headers the response headers
     * @param body the response body, empty for a non-2xx status
     */
    public record Result(int statusCode, HttpHeaders headers, byte[] body) {

        /**
         * Tells whether the status is 2xx.
         *
         * @return {@code true} for a 2xx status
         */
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /**
     * Sends the request and reads the whole response within the timeout.
     *
     * @param client the HTTP client
     * @param request the request
     * @param timeout the deadline of the whole exchange, body included
     * @param sizeLimit the maximum body size, in bytes
     * @return the completed exchange
     * @throws HttpTimeoutException when the exchange does not complete within the timeout
     * @throws IOException when the request fails or the body exceeds the size limit
     * @throws InterruptedException when the calling thread is interrupted while waiting
     */
    public static Result send(HttpClient client, HttpRequest request, Duration timeout, int sizeLimit)
            throws IOException, InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        CompletableFuture<HttpResponse<byte[]>> exchange = client.sendAsync(request, info -> bodySubscriber(info, sizeLimit));
        try {
            HttpResponse<byte[]> response = exchange.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return new Result(response.statusCode(), response.headers(), response.body());
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw new HttpTimeoutException("Request did not complete within " + timeout.toMillis() + " ms");
        } catch (InterruptedException e) {
            exchange.cancel(true);
            throw e;
        } catch (CancellationException e) {
            throw new IOException("Request was cancelled", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Request failed", e.getCause());
        }
    }

    private static HttpResponse.BodySubscriber<byte[]> bodySubscriber(HttpResponse.ResponseInfo info, int sizeLimit) {
        if (info.statusCode() < 200 || info.statusCode() >= 300) {
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        return new LimitedBodySubscriber(sizeLimit);
    }

    /**
     * Collects the body into a byte array and fails, cancelling the subscription, as soon as it exceeds the limit.
     */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int sizeLimit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int sizeLimit) {
            this.sizeLimit = sizeLimit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                if ((long) buffer.size() + item.remaining() > sizeLimit) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Response exceeds " + sizeLimit + " bytes"));
                    return;
                }
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                buffer.write(bytes, 0, bytes.length);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(buffer.toByteArray());
        }
    }
}
