package it.eng.dcp.spi.testkit.defaults;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.participant.ParticipantContextResolver;
import it.eng.dcp.common.participant.PathParticipantContextResolver;
import it.eng.dcp.spi.testkit.ParticipantContextResolverContractTest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs the resolver contract, including its multi-participant cases, against
 * {@link PathParticipantContextResolver} configured with the default base paths and reserved IDs.
 */
class PathParticipantContextResolverContractTest extends ParticipantContextResolverContractTest {

    private static final String BASE_PATH = "dcp";
    private static final Set<String> BASE_PATHS = Set.of(BASE_PATH, "issuer");

    private final Set<String> reservedIds = new DcpProperties().getParticipant().getReservedIds();
    private final ParticipantContextResolver resolver = new PathParticipantContextResolver(BASE_PATHS, reservedIds);

    @Override
    protected ParticipantContextResolver resolver() {
        return resolver;
    }

    @Override
    protected HttpServletRequest requestFor(String participantId) {
        return new MockHttpServletRequest("POST", "/" + participantId + "/" + BASE_PATH + "/presentations/query");
    }

    @Override
    protected HttpServletRequest unscopedRequest() {
        return new MockHttpServletRequest("POST", "/" + BASE_PATH + "/presentations/query");
    }

    @Override
    protected String participantIdA() {
        return "tenant-a";
    }

    @Override
    protected String participantIdB() {
        return "tenant-b";
    }

    @Override
    protected boolean supportsMultipleParticipants() {
        return true;
    }

    @Override
    protected Set<String> reservedIds() {
        Set<String> all = new HashSet<>(reservedIds);
        all.addAll(BASE_PATHS);
        return all;
    }
}
