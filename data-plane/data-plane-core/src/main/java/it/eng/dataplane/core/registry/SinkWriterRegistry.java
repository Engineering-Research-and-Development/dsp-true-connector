package it.eng.dataplane.core.registry;

import it.eng.dataplane.api.io.SinkWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Discovers all {@link SinkWriter} beans and provides lookup by sink type.
 */
@Slf4j
@Component
public class SinkWriterRegistry {

    private final Map<String, SinkWriter> writers;

    /**
     * Constructs the registry from all {@link SinkWriter} beans in the application context.
     *
     * @param writers available sink writers
     */
    public SinkWriterRegistry(List<SinkWriter> writers) {
        this.writers = writers.stream()
                .collect(Collectors.toMap(SinkWriter::getSinkType, Function.identity()));
        log.info("Registered sink writers: {}", this.writers.keySet());
    }

    /**
     * Returns the writer for the given sink type.
     *
     * @param sinkType sink type identifier
     * @return matching writer when available
     */
    public Optional<SinkWriter> getWriter(String sinkType) {
        return Optional.ofNullable(writers.get(sinkType));
    }
}
