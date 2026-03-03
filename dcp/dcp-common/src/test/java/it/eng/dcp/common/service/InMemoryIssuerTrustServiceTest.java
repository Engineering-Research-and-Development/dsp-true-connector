package it.eng.dcp.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryIssuerTrustService}.
 *
 * <p>Covers the full trust lifecycle as well as DID normalization: both
 * URL-encoded DIDs (e.g. {@code did:web:issuer%3A8082:issuer}) and their
 * decoded equivalents (e.g. {@code did:web:issuer:8082:issuer}) must be
 * treated as the same identity.
 */
class InMemoryIssuerTrustServiceTest {

    private static final String MEMBERSHIP_CREDENTIAL = "MembershipCredential";
    private static final String VERIFIABLE_CREDENTIAL = "VerifiableCredential";

    /** Plain (decoded) DID — as it would appear in application.properties when no encoding is used. */
    private static final String DID_PLAIN = "did:web:issuer:8082:issuer";

    /** URL-encoded equivalent of {@link #DID_PLAIN} — as it would appear when the port separator is percent-encoded. */
    private static final String DID_ENCODED = "did:web:issuer%3A8082:issuer";

    private InMemoryIssuerTrustService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryIssuerTrustService();
    }

    // -------------------------------------------------------------------------
    // Basic lifecycle
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Basic trust lifecycle")
    class BasicLifecycle {

        @Test
        @DisplayName("isTrusted returns false before any trust is added")
        void isTrusted_returnsFalseBeforeAdd() {
            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
        }

        @Test
        @DisplayName("getTrustedIssuers returns empty set for unknown credential type")
        void getTrustedIssuers_emptyForUnknownType() {
            assertTrue(service.getTrustedIssuers(MEMBERSHIP_CREDENTIAL).isEmpty());
        }

        @Test
        @DisplayName("addTrust/isTrusted/getTrustedIssuers/removeTrust full lifecycle")
        void fullLifecycle() {
            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));

            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));

            Set<String> issuers = service.getTrustedIssuers(MEMBERSHIP_CREDENTIAL);
            assertEquals(1, issuers.size());

            service.removeTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
            assertTrue(service.getTrustedIssuers(MEMBERSHIP_CREDENTIAL).isEmpty());
        }

        @Test
        @DisplayName("Multiple credential types are tracked independently")
        void multipleCredentialTypes_trackedIndependently() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            service.addTrust(VERIFIABLE_CREDENTIAL, DID_PLAIN);

            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
            assertTrue(service.isTrusted(VERIFIABLE_CREDENTIAL, DID_PLAIN));

            service.removeTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
            assertTrue(service.isTrusted(VERIFIABLE_CREDENTIAL, DID_PLAIN));
        }

        @Test
        @DisplayName("removeTrust on unknown DID is a no-op")
        void removeTrust_unknownDid_isNoOp() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            service.removeTrust(MEMBERSHIP_CREDENTIAL, "did:web:other:issuer");
            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
        }

        @Test
        @DisplayName("getTrustedIssuers returns unmodifiable view")
        void getTrustedIssuers_returnsUnmodifiableSet() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            Set<String> issuers = service.getTrustedIssuers(MEMBERSHIP_CREDENTIAL);
            assertThrows(UnsupportedOperationException.class, () -> issuers.add("did:web:other"));
        }
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("addTrust throws on null credentialType")
        void addTrust_nullCredentialType_throws() {
            assertThrows(IllegalArgumentException.class, () -> service.addTrust(null, DID_PLAIN));
        }

        @Test
        @DisplayName("addTrust throws on blank credentialType")
        void addTrust_blankCredentialType_throws() {
            assertThrows(IllegalArgumentException.class, () -> service.addTrust("  ", DID_PLAIN));
        }

        @Test
        @DisplayName("addTrust throws on null issuerDid")
        void addTrust_nullIssuerDid_throws() {
            assertThrows(IllegalArgumentException.class, () -> service.addTrust(MEMBERSHIP_CREDENTIAL, null));
        }

        @Test
        @DisplayName("addTrust throws on blank issuerDid")
        void addTrust_blankIssuerDid_throws() {
            assertThrows(IllegalArgumentException.class, () -> service.addTrust(MEMBERSHIP_CREDENTIAL, "  "));
        }

        @Test
        @DisplayName("isTrusted returns false for null inputs without throwing")
        void isTrusted_nullInputs_returnsFalse() {
            assertFalse(service.isTrusted(null, DID_PLAIN));
            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, null));
        }
    }

    // -------------------------------------------------------------------------
    // DID normalization
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DID normalization — encoded vs plain")
    class DidNormalization {

        @Test
        @DisplayName("Add encoded DID, look up with plain DID — isTrusted returns true")
        void addEncoded_lookupPlain_isTrusted() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_ENCODED);
            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN),
                "Plain DID should match after encoded DID was registered");
        }

        @Test
        @DisplayName("Add plain DID, look up with encoded DID — isTrusted returns true")
        void addPlain_lookupEncoded_isTrusted() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_ENCODED),
                "Encoded DID should match after plain DID was registered");
        }

        @Test
        @DisplayName("Adding encoded and plain DID results in a single entry (they normalize to the same value)")
        void addEncodedAndPlain_storedAsSingleEntry() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_ENCODED);
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);

            assertEquals(1, service.getTrustedIssuers(MEMBERSHIP_CREDENTIAL).size(),
                "Both forms should collapse to a single normalized DID");
        }

        @Test
        @DisplayName("Remove using plain DID removes entry originally added with encoded DID")
        void removePlain_removesEncodedEntry() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_ENCODED);
            service.removeTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);

            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_ENCODED));
            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
        }

        @Test
        @DisplayName("Remove using encoded DID removes entry originally added with plain DID")
        void removeEncoded_removesPlainEntry() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            service.removeTrust(MEMBERSHIP_CREDENTIAL, DID_ENCODED);

            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
            assertFalse(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_ENCODED));
        }

        @Test
        @DisplayName("VerifiableCredential — encoded DID in config, plain DID at runtime")
        void verifiableCredential_encodedConfig_plainRuntime() {
            // Simulates: dcp.trusted-issuers.VerifiableCredential=did:web:issuer%3A8082:issuer
            service.addTrust(VERIFIABLE_CREDENTIAL, DID_ENCODED);

            // At runtime the JWT issuer claim arrives as the decoded DID
            assertTrue(service.isTrusted(VERIFIABLE_CREDENTIAL, DID_PLAIN));
        }

        @Test
        @DisplayName("MembershipCredential — plain DID in config, encoded DID at runtime")
        void membershipCredential_plainConfig_encodedRuntime() {
            // Simulates: dcp.trusted-issuers.MembershipCredential=did:web:issuer:8082:issuer
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);

            // At runtime the JWT issuer claim arrives percent-encoded
            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_ENCODED));
        }

        @Test
        @DisplayName("Both MembershipCredential and VerifiableCredential — encoded config, plain lookup")
        void bothTypes_encodedConfig_plainLookup() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_ENCODED);
            service.addTrust(VERIFIABLE_CREDENTIAL, DID_ENCODED);

            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_PLAIN));
            assertTrue(service.isTrusted(VERIFIABLE_CREDENTIAL, DID_PLAIN));
        }

        @Test
        @DisplayName("Both MembershipCredential and VerifiableCredential — plain config, encoded lookup")
        void bothTypes_plainConfig_encodedLookup() {
            service.addTrust(MEMBERSHIP_CREDENTIAL, DID_PLAIN);
            service.addTrust(VERIFIABLE_CREDENTIAL, DID_PLAIN);

            assertTrue(service.isTrusted(MEMBERSHIP_CREDENTIAL, DID_ENCODED));
            assertTrue(service.isTrusted(VERIFIABLE_CREDENTIAL, DID_ENCODED));
        }
    }
}

