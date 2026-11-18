package it.eng.tools.auth.keycloak;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Optional;

/**
 * Runs one HTTP call and reads a size-limited body.
 * <p>
 * The deadline of the whole call, body included, is the {@code callTimeout} of the client: a server that sends
 * the headers and then stalls the body is cancelled when it expires. The body of a non-2xx response is not read.
 */
public final class BoundedOkHttpCall {

    private BoundedOkHttpCall() {
    }

    /**
     * Status, content type and body of a completed call.
     *
     * @param statusCode the HTTP status code
     * @param contentType the {@code Content-Type} response header, if present
     * @param body the response body, empty for a non-2xx status
     */
    public record Result(int statusCode, Optional<String> contentType, byte[] body) {

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
     * Executes the request and reads the whole response body, at most {@code sizeLimit} bytes.
     * <p>
     * When the calling thread is interrupted, the {@link InterruptedIOException} of OkHttp is rethrown and the
     * interrupt flag is set.
     *
     * @param client the HTTP client, whose {@code callTimeout} bounds the whole call
     * @param request the request
     * @param sizeLimit the maximum body size, in bytes
     * @return the completed call
     * @throws SocketTimeoutException when the call times out
     * @throws IOException when the request fails or the body exceeds the size limit
     */
    public static Result execute(OkHttpClient client, Request request, int sizeLimit) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            Optional<String> contentType = Optional.ofNullable(response.header("Content-Type"));
            if (!response.isSuccessful()) {
                return new Result(response.code(), contentType, new byte[0]);
            }
            ResponseBody body = response.body();
            if (body == null) {
                return new Result(response.code(), contentType, new byte[0]);
            }
            if (body.contentLength() > sizeLimit) {
                throw new IOException("Response exceeds " + sizeLimit + " bytes");
            }
            BufferedSource source = body.source();
            if (source.request(sizeLimit + 1L)) {
                throw new IOException("Response exceeds " + sizeLimit + " bytes");
            }
            return new Result(response.code(), contentType, source.getBuffer().readByteArray());
        } catch (InterruptedIOException e) {
            if (Thread.currentThread().isInterrupted() || e instanceof SocketTimeoutException) {
                throw e;
            }
            // OkHttp reports an expired call timeout as a plain InterruptedIOException("timeout").
            SocketTimeoutException timeout = new SocketTimeoutException(
                    "Request did not complete within " + client.callTimeoutMillis() + " ms");
            timeout.initCause(e);
            throw timeout;
        }
    }
}
