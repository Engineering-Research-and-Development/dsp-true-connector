package it.eng.dcp.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DidUtils utility class.
 * Tests fragment handling, comparison, and normalization of DIDs.
 */
class DidUtilsTest {

    @Nested
    @DisplayName("stripFragment() tests")
    class StripFragmentTests {

        @Test
        @DisplayName("Strips literal fragment from DID")
        void stripsLiteralFragment() {
            String did = "did:web:example.com:8080#holder";
            String expected = "did:web:example.com:8080";

            String result = DidUtils.stripFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Strips encoded fragment %23 from DID")
        void stripsEncodedFragment() {
            String did = "did:web:localhost%3A8080%23holder";
            String expected = "did:web:localhost:8080";

            String result = DidUtils.stripFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Handles DID with both encoded port and encoded fragment")
        void handlesEncodedPortAndFragment() {
            String did = "did:web:localhost%3A8080:holder%23key-1";
            String expected = "did:web:localhost:8080:holder";

            String result = DidUtils.stripFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Returns same DID when no fragment present")
        void returnsUnchangedWhenNoFragment() {
            String did = "did:web:example.com:8080";

            String result = DidUtils.stripFragment(did);

            assertEquals(did, result);
        }

        @Test
        @DisplayName("Returns same DID when only encoded port present")
        void returnsUnchangedWithOnlyEncodedPort() {
            String did = "did:web:localhost%3A8080:holder";
            String expected = "did:web:localhost:8080:holder";

            String result = DidUtils.stripFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Handles null DID")
        void handlesNullDid() {
            String result = DidUtils.stripFragment(null);

            assertNull(result);
        }

        @Test
        @DisplayName("Handles empty string")
        void handlesEmptyString() {
            String result = DidUtils.stripFragment("");

            assertEquals("", result);
        }

        @Test
        @DisplayName("Strips fragment at end of DID")
        void stripsFragmentAtEnd() {
            String did = "did:web:example.com#key";
            String expected = "did:web:example.com";

            String result = DidUtils.stripFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Handles DID with multiple colons and fragment")
        void handlesMultipleColonsAndFragment() {
            String did = "did:web:example.com:8080:path:to:resource#fragment";
            String expected = "did:web:example.com:8080:path:to:resource";

            String result = DidUtils.stripFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Real-world scenario: localhost with encoded port and fragment")
        void realWorldLocalhostScenario() {
            String did = "did:web:localhost%3A8080%23holder";
            String expected = "did:web:localhost:8080";

            String result = DidUtils.stripFragment(did);

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("extractFragment() tests")
    class ExtractFragmentTests {

        @Test
        @DisplayName("Extracts literal fragment from DID")
        void extractsLiteralFragment() {
            String did = "did:web:example.com:8080#holder";
            String expected = "holder";

            String result = DidUtils.extractFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Extracts encoded fragment %23 from DID")
        void extractsEncodedFragment() {
            String did = "did:web:localhost%3A8080%23key-1";
            String expected = "key-1";

            String result = DidUtils.extractFragment(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Returns null when no fragment present")
        void returnsNullWhenNoFragment() {
            String did = "did:web:example.com:8080";

            String result = DidUtils.extractFragment(did);

            assertNull(result);
        }

        @Test
        @DisplayName("Returns null for null DID")
        void returnsNullForNullDid() {
            String result = DidUtils.extractFragment(null);

            assertNull(result);
        }

        @Test
        @DisplayName("Returns null for empty string")
        void returnsNullForEmptyString() {
            String result = DidUtils.extractFragment("");

            assertNull(result);
        }

        @Test
        @DisplayName("Handles DID ending with # but no fragment value")
        void handlesDidEndingWithHashOnly() {
            String did = "did:web:example.com#";

            String result = DidUtils.extractFragment(did);

            assertNull(result);
        }

        @Test
        @DisplayName("Extracts fragment with special characters")
        void extractsFragmentWithSpecialChars() {
            String did = "did:web:example.com#key-1-test";
            String expected = "key-1-test";

            String result = DidUtils.extractFragment(did);

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("hasFragment() tests")
    class HasFragmentTests {

        @Test
        @DisplayName("Returns true for literal fragment")
        void returnsTrueForLiteralFragment() {
            String did = "did:web:example.com#holder";

            boolean result = DidUtils.hasFragment(did);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns true for encoded fragment %23")
        void returnsTrueForEncodedFragment() {
            String did = "did:web:localhost%3A8080%23holder";

            boolean result = DidUtils.hasFragment(did);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns false when no fragment present")
        void returnsFalseWhenNoFragment() {
            String did = "did:web:example.com:8080";

            boolean result = DidUtils.hasFragment(did);

            assertFalse(result);
        }

        @Test
        @DisplayName("Returns false for null DID")
        void returnsFalseForNull() {
            boolean result = DidUtils.hasFragment(null);

            assertFalse(result);
        }

        @Test
        @DisplayName("Returns true when only encoded fragment present")
        void returnsTrueForOnlyEncodedFragment() {
            String did = "did:web:example.com%23key";

            boolean result = DidUtils.hasFragment(did);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("compareIgnoringFragment() tests")
    class CompareIgnoringFragmentTests {

        @Test
        @DisplayName("Returns true for same DIDs without fragments")
        void returnsTrueForSameDidsWithoutFragments() {
            String did1 = "did:web:example.com:8080";
            String did2 = "did:web:example.com:8080";

            boolean result = DidUtils.compareIgnoringFragment(did1, did2);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns true for same base DID with different fragments")
        void returnsTrueForSameBaseWithDifferentFragments() {
            String did1 = "did:web:example.com:8080#key-1";
            String did2 = "did:web:example.com:8080#key-2";

            boolean result = DidUtils.compareIgnoringFragment(did1, did2);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns true when one has fragment and other doesn't")
        void returnsTrueWhenOnlyOneHasFragment() {
            String did1 = "did:web:example.com:8080#holder";
            String did2 = "did:web:example.com:8080";

            boolean result = DidUtils.compareIgnoringFragment(did1, did2);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns true for encoded and literal fragments")
        void returnsTrueForEncodedAndLiteralFragments() {
            String did1 = "did:web:localhost%3A8080%23holder";
            String did2 = "did:web:localhost:8080#holder";

            boolean result = DidUtils.compareIgnoringFragment(did1, did2);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns false for different base DIDs")
        void returnsFalseForDifferentBaseDids() {
            String did1 = "did:web:example.com:8080#key";
            String did2 = "did:web:other.com:8080#key";

            boolean result = DidUtils.compareIgnoringFragment(did1, did2);

            assertFalse(result);
        }

        @Test
        @DisplayName("Returns true when both are null")
        void returnsTrueWhenBothNull() {
            boolean result = DidUtils.compareIgnoringFragment(null, null);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns false when one is null")
        void returnsFalseWhenOneNull() {
            String did = "did:web:example.com";

            assertFalse(DidUtils.compareIgnoringFragment(null, did));
            assertFalse(DidUtils.compareIgnoringFragment(did, null));
        }
    }

    @Nested
    @DisplayName("isValidAudience() tests")
    class IsValidAudienceTests {

        @Test
        @DisplayName("Returns true for exact match")
        void returnsTrueForExactMatch() {
            String audience = "did:web:verifier.com";
            String expectedDid = "did:web:verifier.com";

            boolean result = DidUtils.isValidAudience(audience, expectedDid);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns true when audience has fragment")
        void returnsTrueWhenAudienceHasFragment() {
            String audience = "did:web:verifier.com#key-1";
            String expectedDid = "did:web:verifier.com";

            boolean result = DidUtils.isValidAudience(audience, expectedDid);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns true for encoded audience with fragment")
        void returnsTrueForEncodedAudienceWithFragment() {
            String audience = "did:web:localhost%3A8080%23holder";
            String expectedDid = "did:web:localhost:8080";

            boolean result = DidUtils.isValidAudience(audience, expectedDid);

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns false for different DIDs")
        void returnsFalseForDifferentDids() {
            String audience = "did:web:attacker.com";
            String expectedDid = "did:web:verifier.com";

            boolean result = DidUtils.isValidAudience(audience, expectedDid);

            assertFalse(result);
        }

        @Test
        @DisplayName("Returns false when audience is null")
        void returnsFalseWhenAudienceNull() {
            String expectedDid = "did:web:verifier.com";

            boolean result = DidUtils.isValidAudience(null, expectedDid);

            assertFalse(result);
        }

        @Test
        @DisplayName("Returns false when expected DID is null")
        void returnsFalseWhenExpectedDidNull() {
            String audience = "did:web:verifier.com";

            boolean result = DidUtils.isValidAudience(audience, null);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("withFragment() tests")
    class WithFragmentTests {

        @Test
        @DisplayName("Adds fragment to base DID")
        void addsFragmentToBaseDid() {
            String baseDid = "did:web:example.com:8080";
            String fragment = "holder";
            String expected = "did:web:example.com:8080#holder";

            String result = DidUtils.withFragment(baseDid, fragment);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Replaces existing fragment")
        void replacesExistingFragment() {
            String baseDid = "did:web:example.com:8080#old";
            String fragment = "new";
            String expected = "did:web:example.com:8080#new";

            String result = DidUtils.withFragment(baseDid, fragment);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Returns base DID when fragment is null")
        void returnsBaseDidWhenFragmentNull() {
            String baseDid = "did:web:example.com:8080";

            String result = DidUtils.withFragment(baseDid, null);

            assertEquals(baseDid, result);
        }

        @Test
        @DisplayName("Returns base DID when fragment is empty")
        void returnsBaseDidWhenFragmentEmpty() {
            String baseDid = "did:web:example.com:8080";

            String result = DidUtils.withFragment(baseDid, "");

            assertEquals(baseDid, result);
        }

        @Test
        @DisplayName("Handles encoded DID with fragment")
        void handlesEncodedDidWithFragment() {
            String baseDid = "did:web:localhost%3A8080%23old";
            String fragment = "new";
            String expected = "did:web:localhost:8080#new";

            String result = DidUtils.withFragment(baseDid, fragment);

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("normalize() tests")
    class NormalizeTests {

        @Test
        @DisplayName("Normalizes DID by removing fragment")
        void normalizesByRemovingFragment() {
            String did = "did:web:example.com:8080#holder";
            String expected = "did:web:example.com:8080";

            String result = DidUtils.normalize(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Normalizes DID with encoded fragment")
        void normalizesEncodedFragment() {
            String did = "did:web:localhost%3A8080%23holder";
            String expected = "did:web:localhost:8080";

            String result = DidUtils.normalize(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Trims whitespace")
        void trimsWhitespace() {
            String did = "  did:web:example.com:8080  ";
            String expected = "did:web:example.com:8080";

            String result = DidUtils.normalize(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Returns null for null input")
        void returnsNullForNull() {
            String result = DidUtils.normalize(null);

            assertNull(result);
        }

        @Test
        @DisplayName("Handles whitespace and fragment together")
        void handlesWhitespaceAndFragment() {
            String did = "  did:web:example.com:8080#key  ";
            String expected = "did:web:example.com:8080";

            String result = DidUtils.normalize(did);

            assertEquals(expected, result);
        }
    }
}
