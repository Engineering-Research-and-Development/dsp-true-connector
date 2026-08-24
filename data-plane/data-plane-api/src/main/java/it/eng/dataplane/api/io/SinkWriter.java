package it.eng.dataplane.api.io;

import java.io.InputStream;

/**
 * Writes stream data to a sink backend.
 */
public interface SinkWriter {

    /**
     * Returns the sink type handled by this writer.
     *
     * @return sink type identifier
     */
    String getSinkType();

    /**
     * Writes the provided data stream to the sink described by the context.
     *
     * @param data data stream to persist
     * @param context sink context
     * @return sink write result
     */
    SinkWriteResult write(InputStream data, SinkContext context);
}
