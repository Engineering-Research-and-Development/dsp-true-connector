package it.eng.dataplane.core.registry;

import it.eng.dataplane.api.io.SourceReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Discovers all {@link SourceReader} beans and provides lookup by source type.
 */
@Slf4j
@Component
public class SourceReaderRegistry {

    private final Map<String, SourceReader> readers;

    /**
     * Constructs the registry from all {@link SourceReader} beans in the application context.
     *
     * @param readers available source readers
     */
    public SourceReaderRegistry(List<SourceReader> readers) {
        this.readers = readers.stream()
                .collect(Collectors.toMap(SourceReader::getSourceType, Function.identity()));
        log.info("Registered source readers: {}", this.readers.keySet());
    }

    /**
     * Returns the reader for the given source type.
     *
     * @param sourceType source type identifier
     * @return matching reader when available
     */
    public Optional<SourceReader> getReader(String sourceType) {
        return Optional.ofNullable(readers.get(sourceType));
    }
}
