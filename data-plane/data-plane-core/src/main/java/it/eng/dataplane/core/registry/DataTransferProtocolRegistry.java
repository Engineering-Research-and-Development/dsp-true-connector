package it.eng.dataplane.core.registry;

import it.eng.dataplane.api.spi.DataTransferProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Discovers all {@link DataTransferProtocol} beans and provides lookup by protocol ID.
 */
@Slf4j
@Component
public class DataTransferProtocolRegistry {

    private final Map<String, DataTransferProtocol> protocols;

    /**
     * Constructs the registry from all DataTransferProtocol beans in the application context.
     *
     * @param protocols all DataTransferProtocol implementations
     */
    public DataTransferProtocolRegistry(List<DataTransferProtocol> protocols) {
        this.protocols = protocols.stream()
            .collect(Collectors.toMap(DataTransferProtocol::getProtocolId, Function.identity()));
        log.info("Registered transfer protocols: {}", this.protocols.keySet());
    }

    /**
     * Returns the protocol implementation for the given protocol ID.
     *
     * @param protocolId e.g. "HttpData-PULL"
     * @return protocol instance or null if not found
     */
    public DataTransferProtocol getProtocol(String protocolId) {
        return protocols.get(protocolId);
    }

    /**
     * Returns an immutable snapshot of all registered protocol IDs.
     * Callers cannot mutate the registry through the returned set.
     *
     * @return immutable set of protocol identifiers
     */
    public Set<String> getSupportedProtocols() {
        return Set.copyOf(protocols.keySet());
    }
}
