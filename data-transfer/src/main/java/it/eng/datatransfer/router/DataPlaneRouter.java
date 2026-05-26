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
import java.util.stream.Collectors;

/**
 * Routes transfer requests to registered Data Plane instances.
 *
 * <p>Provides two selection modes:</p>
 * <ul>
 *   <li><b>Type-only (legacy)</b> — {@link #selectDataPlane(String)} selects by transfer type
 *       with round-robin balancing. Used by existing HTTP-PULL/PUSH flows.</li>
 *   <li><b>Profile-aware sticky</b> — {@link #selectDataPlane(String, String, String)} selects by
 *       transfer type and transport profile, and pins the choice to the {@code processId} so that
 *       all lifecycle calls (start, terminate, suspend, resume) reach the same Data Plane instance.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlaneRouter {

    private final DataPlaneRegistrationService registrationService;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Sticky map from transfer-process ID to the endpoint of the selected Data Plane.
     * Ensures repeated calls for the same process always reach the same instance.
     */
    private final ConcurrentHashMap<String, String> stickyMap = new ConcurrentHashMap<>();

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

    /**
     * Selects a Data Plane by transfer type and transport profile, with sticky routing by processId.
     *
     * <p>On the first call for a given {@code processId}, candidates are filtered by {@code transferType}
     * and, when {@code transportProfile} is non-null, further filtered to those that advertise the profile.
     * The selected endpoint is pinned to the {@code processId} so that subsequent calls always return
     * the same Data Plane instance.</p>
     *
     * <p>If {@code transportProfile} is non-null and no registered Data Plane advertises that profile,
     * an {@link IllegalStateException} is thrown immediately — there is no silent fallback to HTTP.</p>
     *
     * @param transferType     the transfer type identifier (e.g. {@code "HttpData-PULL"}, {@code "stream:grpc"})
     * @param processId        the transfer process ID used as the sticky-routing key; may be {@code null}
     *                         for stateless selection
     * @param transportProfile the required transport profile (e.g. {@code "stream:grpc"}),
     *                         or {@code null} to match any registered Data Plane for the given type
     * @return an {@link Optional} containing the selected {@link DataPlaneRegistration},
     *         or {@link Optional#empty()} if no Data Plane supports the type and no profile was requested
     * @throws IllegalStateException if {@code transportProfile} is non-null and no Data Plane
     *                               advertising that profile is registered for the given transfer type
     */
    public Optional<DataPlaneRegistration> selectDataPlane(String transferType,
                                                           String processId,
                                                           String transportProfile) {
        if (processId != null) {
            String stickyEndpoint = stickyMap.get(processId);
            if (stickyEndpoint != null) {
                List<DataPlaneRegistration> candidates = findCandidates(transferType, transportProfile);
                Optional<DataPlaneRegistration> stickyDp = candidates.stream()
                        .filter(r -> r.getEndpoint().equals(stickyEndpoint))
                        .findFirst();
                if (stickyDp.isPresent()) {
                    log.debug("Reusing sticky Data Plane '{}' for process '{}'", stickyEndpoint, processId);
                    return stickyDp;
                }
                log.warn("Sticky Data Plane '{}' no longer available for process '{}', re-selecting",
                        stickyEndpoint, processId);
                stickyMap.remove(processId);
            }
        }

        List<DataPlaneRegistration> candidates = findCandidates(transferType, transportProfile);

        if (candidates.isEmpty()) {
            if (transportProfile != null) {
                throw new IllegalStateException(
                        "No Data Plane registered for transfer type '" + transferType
                                + "' and transport profile '" + transportProfile + "'");
            }
            log.warn("No Data Plane registered for transfer type '{}'", transferType);
            return Optional.empty();
        }

        String counterKey = transferType + ":" + transportProfile;
        AtomicInteger counter = counters.computeIfAbsent(counterKey, k -> new AtomicInteger(0));
        int index = (counter.getAndIncrement() & Integer.MAX_VALUE) % candidates.size();
        DataPlaneRegistration selected = candidates.get(index);

        if (processId != null) {
            stickyMap.put(processId, selected.getEndpoint());
        }
        log.debug("Selected Data Plane '{}' for transfer type '{}', profile '{}', process '{}'",
                selected.getEndpoint(), transferType, transportProfile, processId);
        return Optional.of(selected);
    }

    private List<DataPlaneRegistration> findCandidates(String transferType, String transportProfile) {
        List<DataPlaneRegistration> all = registrationService.findByTransferType(transferType);
        if (transportProfile == null) {
            return all;
        }
        return all.stream()
                .filter(r -> r.getTransportProfiles() != null
                        && r.getTransportProfiles().contains(transportProfile))
                .collect(Collectors.toList());
    }
}
