package it.eng.dcp.common.participant;

import it.eng.dcp.common.model.ServiceEntry;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantContextTest {

    private static final SigningKeyReference KEY = new SigningKeyReference("eckey.p12", "alias");

    @Test
    @DisplayName("a valid context reports its state")
    void validContext() {
        ParticipantContext enabled = new ParticipantContext("p1", "did:web:a", KEY, List.of(), Map.of(), null,
                ParticipantState.ENABLED);
        ParticipantContext disabled = new ParticipantContext("p2", "did:web:b", KEY, List.of(), Map.of(), null,
                ParticipantState.DISABLED);

        assertTrue(enabled.isEnabled());
        assertFalse(disabled.isEnabled());
    }

    @Test
    @DisplayName("a missing id, DID, signing key or state is rejected")
    void invalidContext() {
        assertThrows(ValidationException.class, () -> new ParticipantContext(" ", "did:web:a", KEY, null, null, null,
                ParticipantState.ENABLED));
        assertThrows(ValidationException.class, () -> new ParticipantContext("p", null, KEY, null, null, null,
                ParticipantState.ENABLED));
        assertThrows(ValidationException.class, () -> new ParticipantContext("p", "did:web:a", null, null, null, null,
                ParticipantState.ENABLED));
        assertThrows(ValidationException.class, () -> new ParticipantContext("p", "did:web:a", KEY, null, null, null,
                null));
    }

    @Test
    @DisplayName("collections are defensively copied and never null")
    void collectionsAreImmutable() {
        List<ServiceEntry> services = new ArrayList<>(List.of(new ServiceEntry("s", "CredentialService", "https://h")));
        ParticipantContext context = new ParticipantContext("p", "did:web:a", KEY, services, null, null,
                ParticipantState.ENABLED);
        services.clear();

        assertEquals(1, context.serviceEntries().size());
        assertTrue(context.trustedIssuers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> context.serviceEntries().clear());
    }

    @Test
    @DisplayName("a signing key reference needs a keystore path and an alias")
    void signingKeyReferenceValidation() {
        assertThrows(ValidationException.class, () -> new SigningKeyReference("", "alias"));
        assertThrows(ValidationException.class, () -> new SigningKeyReference("eckey.p12", null));
    }
}
