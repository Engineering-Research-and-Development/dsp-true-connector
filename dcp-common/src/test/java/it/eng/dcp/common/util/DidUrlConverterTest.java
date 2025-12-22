package it.eng.dcp.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DidUrlConverter utility class.
 * Tests URL to DID conversion, DID validation, and reverse conversion.
 */
class DidUrlConverterTest {

    @Nested
    @DisplayName("convertUrlToDid() tests")
    class ConvertUrlToDidTests {

        @Test
        @DisplayName("Converts simple HTTPS URL to DID")
        void convertsSimpleHttpsUrl() {
            String url = "https://verifier.example.com/catalog/request";
            String expected = "did:web:verifier.example.com";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts localhost with port to DID with encoded colon")
        void convertsLocalhostWithPort() {
            String url = "https://localhost:8080/dsp/catalog";
            String expected = "did:web:localhost%3A8080";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts URL with custom port to DID with encoded colon")
        void convertsUrlWithCustomPort() {
            String url = "https://connector.example.com:9090/api/endpoint";
            String expected = "did:web:connector.example.com%3A9090";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Ignores default HTTPS port 443")
        void ignoresDefaultHttpsPort() {
            String url = "https://verifier.example.com:443/catalog";
            String expected = "did:web:verifier.example.com";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Ignores default HTTP port 80")
        void ignoresDefaultHttpPort() {
            String url = "http://verifier.example.com:80/catalog";
            String expected = "did:web:verifier.example.com";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts URL without path")
        void convertsUrlWithoutPath() {
            String url = "https://verifier.example.com";
            String expected = "did:web:verifier.example.com";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Ignores path and query parameters")
        void ignoresPathAndQueryParams() {
            String url = "https://verifier.com/catalog/request?param=value";
            String expected = "did:web:verifier.com";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Handles subdomain correctly")
        void handlesSubdomain() {
            String url = "https://api.connector.example.com/dcp/catalog";
            String expected = "did:web:api.connector.example.com";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Handles IP address")
        void handlesIpAddress() {
            String url = "https://192.168.1.100:8080/catalog";
            String expected = "did:web:192.168.1.100%3A8080";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Throws exception for null URL")
        void throwsExceptionForNullUrl() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertUrlToDid(null)
            );

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Throws exception for empty URL")
        void throwsExceptionForEmptyUrl() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertUrlToDid("")
            );

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Throws exception for blank URL")
        void throwsExceptionForBlankUrl() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertUrlToDid("   ")
            );

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Throws exception for malformed URL")
        void throwsExceptionForMalformedUrl() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertUrlToDid("not-a-valid-url")
            );

            assertTrue(exception.getMessage().contains("Invalid target URL"));
        }

        @Test
        @DisplayName("Throws exception for URL without protocol")
        void throwsExceptionForUrlWithoutProtocol() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertUrlToDid("verifier.example.com/catalog")
            );

            assertTrue(exception.getMessage().contains("Invalid target URL"));
        }
    }

    @Nested
    @DisplayName("extractBaseUrl() tests")
    class ExtractBaseUrlTests {

        @Test
        @DisplayName("Extracts base URL from full URL with path")
        void extractsBaseUrlWithPath() {
            String url = "https://verifier.example.com/catalog/request";
            String expected = "https://verifier.example.com";

            String result = DidUrlConverter.extractBaseUrl(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Extracts base URL with port")
        void extractsBaseUrlWithPort() {
            String url = "https://localhost:8080/dsp/catalog";
            String expected = "https://localhost:8080";

            String result = DidUrlConverter.extractBaseUrl(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Extracts base URL without path")
        void extractsBaseUrlWithoutPath() {
            String url = "https://verifier.example.com";
            String expected = "https://verifier.example.com";

            String result = DidUrlConverter.extractBaseUrl(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Ignores default HTTPS port in base URL")
        void ignoresDefaultHttpsPortInBaseUrl() {
            String url = "https://verifier.example.com:443/catalog";
            String expected = "https://verifier.example.com";

            String result = DidUrlConverter.extractBaseUrl(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Includes custom port in base URL")
        void includesCustomPortInBaseUrl() {
            String url = "https://verifier.example.com:9090/api/endpoint";
            String expected = "https://verifier.example.com:9090";

            String result = DidUrlConverter.extractBaseUrl(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Handles HTTP protocol")
        void handlesHttpProtocol() {
            String url = "http://verifier.example.com/catalog";
            String expected = "http://verifier.example.com";

            String result = DidUrlConverter.extractBaseUrl(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Throws exception for null URL")
        void throwsExceptionForNullUrl() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.extractBaseUrl(null)
            );

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Throws exception for empty URL")
        void throwsExceptionForEmptyUrl() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.extractBaseUrl("")
            );

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Throws exception for malformed URL")
        void throwsExceptionForMalformedUrl() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.extractBaseUrl("invalid-url")
            );

            assertTrue(exception.getMessage().contains("Invalid target URL"));
        }
    }

    @Nested
    @DisplayName("isValidDidWeb() tests")
    class IsValidDidWebTests {

        @Test
        @DisplayName("Validates correct DID:web format")
        void validatesCorrectDidWeb() {
            String did = "did:web:verifier.example.com";

            boolean result = DidUrlConverter.isValidDidWeb(did);

            assertTrue(result);
        }

        @Test
        @DisplayName("Validates DID:web with port encoding")
        void validatesDidWebWithPort() {
            String did = "did:web:localhost%3A8080";

            boolean result = DidUrlConverter.isValidDidWeb(did);

            assertTrue(result);
        }

        @Test
        @DisplayName("Validates DID:web with path")
        void validatesDidWebWithPath() {
            String did = "did:web:example.com:holder";

            boolean result = DidUrlConverter.isValidDidWeb(did);

            assertTrue(result);
        }

        @Test
        @DisplayName("Rejects null DID")
        void rejectsNullDid() {
            boolean result = DidUrlConverter.isValidDidWeb(null);

            assertFalse(result);
        }

        @Test
        @DisplayName("Rejects empty DID")
        void rejectsEmptyDid() {
            boolean result = DidUrlConverter.isValidDidWeb("");

            assertFalse(result);
        }

        @Test
        @DisplayName("Rejects blank DID")
        void rejectsBlankDid() {
            boolean result = DidUrlConverter.isValidDidWeb("   ");

            assertFalse(result);
        }

        @Test
        @DisplayName("Rejects DID without web method")
        void rejectsDidWithoutWebMethod() {
            String did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK";

            boolean result = DidUrlConverter.isValidDidWeb(did);

            assertFalse(result);
        }

        @Test
        @DisplayName("Rejects DID:web without identifier")
        void rejectsDidWebWithoutIdentifier() {
            String did = "did:web:";

            boolean result = DidUrlConverter.isValidDidWeb(did);

            assertFalse(result);
        }

        @Test
        @DisplayName("Rejects plain URL")
        void rejectsPlainUrl() {
            String url = "https://verifier.example.com";

            boolean result = DidUrlConverter.isValidDidWeb(url);

            assertFalse(result);
        }

        @Test
        @DisplayName("Rejects partial DID prefix")
        void rejectsPartialDidPrefix() {
            String did = "did:we:example.com";

            boolean result = DidUrlConverter.isValidDidWeb(did);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("convertDidToUrl() tests")
    class ConvertDidToUrlTests {

        @Test
        @DisplayName("Converts simple DID to HTTPS URL")
        void convertsSimpleDidToUrl() {
            String did = "did:web:verifier.example.com";
            String expected = "https://verifier.example.com";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts DID with port encoding to URL")
        void convertsDidWithPortToUrl() {
            String did = "did:web:localhost%3A8080";
            String expected = "https://localhost:8080";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts DID with custom port to URL")
        void convertsDidWithCustomPortToUrl() {
            String did = "did:web:verifier.example.com%3A9090";
            String expected = "https://verifier.example.com:9090";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Ignores path in DID conversion")
        void ignoresPathInDid() {
            String did = "did:web:example.com:holder";
            String expected = "https://example.com";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts DID with IP address")
        void convertsDidWithIpAddress() {
            String did = "did:web:192.168.1.100%3A8080";
            String expected = "https://192.168.1.100:8080";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts DID with port and path segments to URL")
        void convertsDidWithPortAndPathToUrl() {
            String did = "did:web:localhost%3A8080:api:v1";
            String expected = "https://localhost:8080/api/v1";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts DID with host and multiple path segments to URL")
        void convertsDidWithHostAndMultiplePathToUrl() {
            String did = "did:web:example.com:api:v1:resource";
            String expected = "https://example.com/api/v1/resource";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts DID with port (unencoded colon) and path segments to URL")
        void convertsDidWithUnencodedPortAndPathToUrl() {
            String did = "did:web:localhost:8080:api:v1";
            String expected = "https://localhost:8080/api/v1";

            String result = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Throws exception for null DID")
        void throwsExceptionForNullDid() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertDidToUrl(null)
            );

            assertTrue(exception.getMessage().contains("Invalid DID:web format"));
        }

        @Test
        @DisplayName("Throws exception for non-DID:web format")
        void throwsExceptionForNonDidWeb() {
            String did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK";

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertDidToUrl(did)
            );

            assertTrue(exception.getMessage().contains("Invalid DID:web format"));
        }

        @Test
        @DisplayName("Throws exception for plain URL")
        void throwsExceptionForPlainUrl() {
            String url = "https://verifier.example.com";

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DidUrlConverter.convertDidToUrl(url)
            );

            assertTrue(exception.getMessage().contains("Invalid DID:web format"));
        }
    }

    @Nested
    @DisplayName("Round-trip conversion tests")
    class RoundTripTests {

        @Test
        @DisplayName("URL to DID to URL round-trip preserves base URL")
        void urlToDidToUrlRoundTrip() {
            String originalUrl = "https://verifier.example.com/catalog/request";
            String expectedBaseUrl = "https://verifier.example.com";

            String did = DidUrlConverter.convertUrlToDid(originalUrl);
            String resultUrl = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expectedBaseUrl, resultUrl);
        }

        @Test
        @DisplayName("URL with port to DID to URL round-trip")
        void urlWithPortToDidToUrlRoundTrip() {
            String originalUrl = "https://localhost:8080/dsp/catalog";
            String expectedBaseUrl = "https://localhost:8080";

            String did = DidUrlConverter.convertUrlToDid(originalUrl);
            String resultUrl = DidUrlConverter.convertDidToUrl(did);

            assertEquals(expectedBaseUrl, resultUrl);
        }

        @Test
        @DisplayName("DID to URL to DID round-trip preserves DID")
        void didToUrlToDidRoundTrip() {
            String originalDid = "did:web:verifier.example.com";

            String url = DidUrlConverter.convertDidToUrl(originalDid);
            String resultDid = DidUrlConverter.convertUrlToDid(url);

            assertEquals(originalDid, resultDid);
        }

        @Test
        @DisplayName("DID with port to URL to DID round-trip")
        void didWithPortToUrlToDidRoundTrip() {
            String originalDid = "did:web:localhost%3A8080";

            String url = DidUrlConverter.convertDidToUrl(originalDid);
            String resultDid = DidUrlConverter.convertUrlToDid(url);

            assertEquals(originalDid, resultDid);
        }
    }

    @Nested
    @DisplayName("Real-world scenario tests")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("Converts typical DSP catalog request URL")
        void convertsDspCatalogRequest() {
            String url = "https://connector-a.dataspace.org/dsp/catalog/request";
            String expected = "did:web:connector-a.dataspace.org";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts localhost development URL")
        void convertsLocalhostDevUrl() {
            String url = "https://localhost:8081/dcp/presentations/query";
            String expected = "did:web:localhost%3A8081";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Converts production connector URL with custom port")
        void convertsProductionUrlWithPort() {
            String url = "https://connector-prod.example.com:9443/api/endpoint";
            String expected = "did:web:connector-prod.example.com%3A9443";

            String result = DidUrlConverter.convertUrlToDid(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Validates extracted DID is valid DID:web")
        void validateExtractedDidIsValid() {
            String url = "https://verifier.example.com/catalog/request";

            String did = DidUrlConverter.convertUrlToDid(url);
            boolean isValid = DidUrlConverter.isValidDidWeb(did);

            assertTrue(isValid);
        }

        @Test
        @DisplayName("Multiple verifiers get different DIDs")
        void multipleVerifiersGetDifferentDids() {
            String urlA = "https://verifier-a.com/catalog";
            String urlB = "https://verifier-b.com/catalog";
            String urlC = "https://localhost:8081/catalog";

            String didA = DidUrlConverter.convertUrlToDid(urlA);
            String didB = DidUrlConverter.convertUrlToDid(urlB);
            String didC = DidUrlConverter.convertUrlToDid(urlC);

            assertEquals("did:web:verifier-a.com", didA);
            assertEquals("did:web:verifier-b.com", didB);
            assertEquals("did:web:localhost%3A8081", didC);

            // All should be different
            assertNotEquals(didA, didB);
            assertNotEquals(didB, didC);
            assertNotEquals(didA, didC);
        }
    }
}
