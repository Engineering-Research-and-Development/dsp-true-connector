package it.eng.dataplane.api.io;

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
     * <p>All outcomes — including I/O and configuration errors — are returned as a
     * {@link SourceOpenResult} so callers (e.g. gRPC transfer logic) can handle them
     * uniformly without catching checked exceptions. Precondition violations (missing
     * required context properties) may still propagate as unchecked exceptions.</p>
     *
     * @param context source context
     * @return opened source result; never {@code null}
     */
    SourceOpenResult open(SourceContext context);
}
