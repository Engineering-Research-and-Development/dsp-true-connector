package it.eng.dcp.common.participant;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolver for a multi-participant deployment: the participant ID is the path segment that precedes the
 * DCP base path, as in {@code /<participantId>/dcp/...} and {@code /<participantId>/issuer/...}.
 * An unscoped request never resolves to a participant, and neither does a reserved ID.
 */
public class PathParticipantContextResolver implements ParticipantContextResolver {

    private final Set<String> basePaths;
    private final Set<String> reservedIds;

    /**
     * Creates the resolver.
     *
     * @param basePaths   the configured DCP base paths, for example {@code dcp} and {@code issuer}
     * @param reservedIds IDs that must never be resolved, for example zone prefixes
     */
    public PathParticipantContextResolver(Set<String> basePaths, Set<String> reservedIds) {
        this.basePaths = basePaths.stream().map(PathParticipantContextResolver::normalize).collect(Collectors.toUnmodifiableSet());
        this.reservedIds = Stream.concat(reservedIds.stream(), basePaths.stream())
                .map(PathParticipantContextResolver::normalize).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return Optional.empty();
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        List<String> segments = List.of(path.split("/")).stream().filter(s -> !s.isEmpty()).toList();
        if (segments.size() < 2 || !basePaths.contains(normalize(segments.get(1)))) {
            return Optional.empty();
        }
        String candidate = segments.get(0);
        return reservedIds.contains(normalize(candidate)) ? Optional.empty() : Optional.of(candidate);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
