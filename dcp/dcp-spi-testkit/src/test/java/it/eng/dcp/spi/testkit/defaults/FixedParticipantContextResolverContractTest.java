package it.eng.dcp.spi.testkit.defaults;

import it.eng.dcp.common.participant.FixedParticipantContextResolver;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextResolver;
import it.eng.dcp.spi.testkit.ParticipantContextResolverContractTest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Runs the resolver contract against {@link FixedParticipantContextResolver}, which resolves every request
 * to the one configured participant.
 */
class FixedParticipantContextResolverContractTest extends ParticipantContextResolverContractTest {

    private final ParticipantContextResolver resolver = new FixedParticipantContextResolver(ParticipantContext.DEFAULT_ID);

    @Override
    protected ParticipantContextResolver resolver() {
        return resolver;
    }

    @Override
    protected HttpServletRequest requestFor(String participantId) {
        return new MockHttpServletRequest("POST", "/dcp/presentations/query");
    }

    @Override
    protected HttpServletRequest unscopedRequest() {
        return new MockHttpServletRequest("POST", "/dcp/presentations/query");
    }

    @Override
    protected String participantIdA() {
        return ParticipantContext.DEFAULT_ID;
    }

    @Override
    protected String participantIdB() {
        throw new UnsupportedOperationException("The fixed resolver serves a single participant");
    }

    @Override
    protected boolean supportsMultipleParticipants() {
        return false;
    }
}
