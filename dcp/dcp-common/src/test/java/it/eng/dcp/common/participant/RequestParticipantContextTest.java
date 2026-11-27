package it.eng.dcp.common.participant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestParticipantContextTest {

    private static final ParticipantContext ENABLED = participant("p1", ParticipantState.ENABLED);
    private static final ParticipantContext DISABLED = participant("off", ParticipantState.DISABLED);
    private static final Principal ADMIN = () -> "admin";

    private final ParticipantContextProvider provider = id -> switch (id) {
        case "p1" -> Optional.of(ENABLED);
        case "off" -> Optional.of(DISABLED);
        default -> Optional.empty();
    };

    private static ParticipantContext participant(String id, ParticipantState state) {
        return new ParticipantContext(id, "did:web:" + id, new SigningKeyReference("k.p12", "a"), List.of(), Map.of(),
                null, state);
    }

    private static RequestParticipantContext helper(ParticipantContextResolver resolver,
                                                    ParticipantContextProvider provider,
                                                    ParticipantAdminAuthorization authorization) {
        return new RequestParticipantContext(resolver, provider, authorization);
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/x");
    }

    @Test
    @DisplayName("an enabled participant is returned")
    void enabledParticipant() {
        RequestParticipantContext helper = helper(r -> Optional.of("p1"), provider, (p, c) -> true);

        assertEquals(ENABLED, helper.require(request()));
    }

    @Test
    @DisplayName("an unknown participant, a disabled one and an unscoped request are all not found")
    void notFoundCases() {
        assertThrows(ParticipantNotFoundException.class,
                () -> helper(r -> Optional.of("nobody"), provider, (p, c) -> true).require(request()));
        assertThrows(ParticipantNotFoundException.class,
                () -> helper(r -> Optional.of("off"), provider, (p, c) -> true).require(request()));
        assertThrows(ParticipantNotFoundException.class,
                () -> helper(r -> Optional.empty(), provider, (p, c) -> true).require(request()));
    }

    @Test
    @DisplayName("an administration principal that is not authorized for the participant is forbidden")
    void adminForbidden() {
        RequestParticipantContext helper = helper(r -> Optional.of("p1"), provider, (p, c) -> false);

        assertThrows(ParticipantForbiddenException.class, () -> helper.requireForAdmin(request(), ADMIN));
    }

    @Test
    @DisplayName("an authorized administration principal gets the participant")
    void adminAllowed() {
        RequestParticipantContext helper = helper(r -> Optional.of("p1"), provider,
                new AllowAllAdminsParticipantAdminAuthorization());

        assertEquals(ENABLED, helper.requireForAdmin(request(), ADMIN));
        assertTrue(new AllowAllAdminsParticipantAdminAuthorization().isAllowed(ADMIN, ENABLED));
    }
}
