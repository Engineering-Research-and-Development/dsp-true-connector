package it.eng.datatransfer.router;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes transfer requests to registered Data Plane instances using round-robin selection.
 *
 * <p>For each transfer type, candidates are retrieved from {@link DataPlaneRegistrationService}
 * and the next candidate is selected by cycling through the list atomically.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlaneRouter {

    private final DataPlaneRegistrationService registrationService;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Selects a Data Plane registration capable of handling the given transfer type.
     *
     * <p>Candidates are retrieved via {@link DataPlaneRegistrationService#findByTransferType(String)}.
     * If more than one candidate exists, selection cycles through them in round-robin order.</p>
     *
     * @param transferType the transfer type identifier (e.g. {@code "HttpData-PULL"})
     * @return an {@link Optional} containing the selected {@link DataPlaneRegistration},
     *         or {@link Optional#empty()} if no Data Plane supports the requested type
     */
    public Optional<DataPlaneRegistration> selectDataPlane(String transferType) {
        List<DataPlaneRegistration> candidates = registrationService.findByTransferType(transferType);
        if (candidates.isEmpty()) {
            log.warn("No Data Plane registered for transfer type '{}'", transferType);
            return Optional.empty();
        }
        AtomicInteger counter = counters.computeIfAbsent(transferType, k -> new AtomicInteger(0));
        int index = (counter.getAndIncrement() & Integer.MAX_VALUE) % candidates.size();
        DataPlaneRegistration selected = candidates.get(index);
        log.debug("Selected Data Plane '{}' for transfer type '{}'", selected.getEndpoint(), transferType);
        return Optional.of(selected);
    }
}
