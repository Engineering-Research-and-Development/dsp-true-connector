package it.eng.dcp.spi.testkit;

import it.eng.dcp.common.participant.ParticipantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract every {@link ParticipantContextResolver} must honour. A host subclasses it, supplies the
 * fixtures and inherits the tests.
 *
 * <p>JUnit creates a new instance per test method, so create fixtures in a {@code @BeforeEach} method
 * of the subclass instead of sharing them statically.
 */
public abstract class ParticipantContextResolverContractTest {

    /**
     * IDs that collide with zone prefixes and must never resolve, whatever the implementation. They are
     * always checked, in addition to {@link #reservedIds()}.
     */
    protected static final Set<String> SPEC_RESERVED_IDS = Set.of("internal", "api", "actuator", ".well-known");

    /**
     * Returns the implementation under test.
     *
     * @return the resolver
     */
    protected abstract ParticipantContextResolver resolver();

    /**
     * Builds a request the way a client addresses the given participant, for example
     * {@code /<participantId>/dcp/presentations/query} for a path-based resolver. For a resolver that
     * ignores the request, any request will do.
     *
     * @param participantId the participant, or a reserved ID
     * @return the request
     */
    protected abstract HttpServletRequest requestFor(String participantId);

    /**
     * Builds a request that addresses no participant, for example {@code /dcp/presentations/query}.
     *
     * @return the unscoped request
     */
    protected abstract HttpServletRequest unscopedRequest();

    /**
     * Returns the ID of an existing participant.
     *
     * @return the ID of the first participant
     */
    protected abstract String participantIdA();

    /**
     * Returns the ID of a second existing participant. Only called when
     * {@link #supportsMultipleParticipants()} is {@code true}.
     *
     * @return the ID of the second participant
     */
    protected abstract String participantIdB();

    /**
     * Declares whether the implementation distinguishes more than one participant. The multi-participant
     * cases are skipped when it does not, because resolving every request to the single participant is
     * then correct.
     *
     * @return {@code true} if the implementation distinguishes more than one participant
     */
    protected abstract boolean supportsMultipleParticipants();

    /**
     * Returns the IDs the implementation reserves, such as zone prefixes and DCP base paths. The default
     * is {@link #SPEC_RESERVED_IDS}; override it to add the IDs of your deployment.
     *
     * @return the reserved IDs
     */
    protected Set<String> reservedIds() {
        return SPEC_RESERVED_IDS;
    }

    @Test
    @DisplayName("a request addressed to a participant resolves to that participant")
    void scopedRequestResolvesToItsParticipant() {
        assertThat(resolver().resolve(requestFor(participantIdA()))).contains(participantIdA());
        if (supportsMultipleParticipants()) {
            assertThat(resolver().resolve(requestFor(participantIdB()))).contains(participantIdB());
        }
    }

    @Test
    @DisplayName("an unscoped request never resolves to an arbitrary participant")
    void unscopedRequestDoesNotResolveInMultiParticipantMode() {
        assumeTrue(supportsMultipleParticipants(), "the implementation serves a single participant");

        assertThat(resolver().resolve(unscopedRequest())).isEmpty();
    }

    @Test
    @DisplayName("reserved IDs are never resolved")
    void reservedIdsAreNeverResolved() {
        Set<String> reserved = new HashSet<>(SPEC_RESERVED_IDS);
        reserved.addAll(reservedIds());

        for (String id : reserved) {
            Optional<String> resolved = resolver().resolve(requestFor(id));
            assertThat(resolved).as("resolution of the reserved ID '%s'", id).isNotEqualTo(Optional.of(id));
        }
    }

    @Test
    @DisplayName("resolution is deterministic for the same request")
    void resolutionIsDeterministic() {
        HttpServletRequest request = requestFor(participantIdA());

        Optional<String> first = resolver().resolve(request);

        assertThat(resolver().resolve(request)).isEqualTo(first);
        assertThat(resolver().resolve(requestFor(participantIdA()))).isEqualTo(first);
        assertThat(resolver().resolve(unscopedRequest())).isEqualTo(resolver().resolve(unscopedRequest()));
    }
}
