package it.eng.dataplane.api.io;

import java.io.IOException;

/**
 * Opens data from a source backend as a readable stream.
 */
public interface SourceReader {

    /**
     * Returns the source type handled by this reader.
     *
     * @return source type identifier
     */
    String getSourceType();

    /**
     * Opens the source represented by the given context.
     *
     * @param context source context
     * @return opened source result
     * @throws IOException if the source cannot be opened
     */
    SourceOpenResult open(SourceContext context) throws IOException;
}
