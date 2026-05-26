package it.eng.dataplane.grpc.io;

import io.grpc.StatusRuntimeException;
import it.eng.dataplane.grpc.proto.DataChunk;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Adapts a gRPC chunk iterator to a standard {@link InputStream}.
 */
@Slf4j
public class GrpcChunkInputStream extends InputStream {

    private final Iterator<DataChunk> iterator;
    private byte[] current;
    private int offset;

    /**
     * Creates a new stream backed by the given chunk iterator.
     *
     * @param iterator gRPC chunk iterator
     */
    public GrpcChunkInputStream(Iterator<DataChunk> iterator) {
        this.iterator = iterator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        if (current == null || offset >= current.length) {
            if (!advance()) {
                return -1;
            }
        }
        return current[offset++] & 0xFF;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("buffer must not be null");
        }
        if (off < 0 || len < 0 || len > buffer.length - off) {
            throw new IndexOutOfBoundsException("Invalid offset/length combination");
        }
        if (len == 0) {
            return 0;
        }
        if (current == null || offset >= current.length) {
            if (!advance()) {
                return -1;
            }
        }
        int toRead = Math.min(len, current.length - offset);
        System.arraycopy(current, offset, buffer, off, toRead);
        offset += toRead;
        return toRead;
    }

    /**
     * Advances to the next non-empty chunk.
     *
     * @return {@code true} when a next chunk is available, {@code false} on EOF
     * @throws IOException if the gRPC stream terminates with an error
     */
    private boolean advance() throws IOException {
        try {
            if (!iterator.hasNext()) {
                return false;
            }
            current = iterator.next().getPayload().toByteArray();
            offset = 0;
            return current.length > 0 || advance();
        } catch (StatusRuntimeException exception) {
            log.debug("gRPC stream interrupted: {}", exception.getStatus());
            throw new IOException("gRPC stream interrupted: " + exception.getStatus(), exception);
        }
    }
}
