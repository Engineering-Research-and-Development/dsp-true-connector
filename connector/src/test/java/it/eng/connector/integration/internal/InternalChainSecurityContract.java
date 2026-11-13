package it.eng.connector.integration.internal;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.auth.internal.InternalCallTestTokens;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security cases of the {@code /internal/**} chain that must hold in every enforcing authentication mode.
 *
 * <p>Implemented by one integration test per mode ({@code INTERNAL} and {@code KEYCLOAK}). MockMvc requests
 * come from {@code 127.0.0.1} to a local {@code 127.0.0.1} socket unless a case sets other addresses.
 */
interface InternalChainSecurityContract {

    /** Tenant of the protocol route used by the cross-zone cases. */
    String PROTOCOL_TENANT_ID = "engineering-a";

    /**
     * Returns the application's MockMvc.
     *
     * @return the MockMvc instance
     */
    MockMvc internalChainMockMvc();

    /**
     * Returns the running application's issuer bean.
     *
     * @return the issuer bean
     */
    InternalCallTokenIssuer internalCallTokenIssuer();

    /**
     * Returns the ID of a stored dataset with formats.
     *
     * @return the dataset ID
     */
    String internalChainDatasetId();

    /**
     * Returns a token that is valid in this mode for a human administrator.
     *
     * @return the administrator's access token
     * @throws Exception when the token cannot be obtained
     */
    String administratorToken() throws Exception;

    private MockHttpServletRequestBuilder formats() {
        return get(InternalChainFixtures.formatsPath(internalChainDatasetId()));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("/internal: a valid internal-call token from loopback returns 200")
    default void validTokenFromLoopbackReturnsOk() throws Exception {
        internalChainMockMvc().perform(formats()
                        .header(HttpHeaders.AUTHORIZATION, internalCallTokenIssuer().authorizationHeader(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("HttpData-PULL"));
    }

    @Test
    @DisplayName("/internal: a valid internal-call token from IPv6 loopback returns 200")
    default void validTokenFromIpv6LoopbackReturnsOk() throws Exception {
        internalChainMockMvc().perform(formats()
                        .with(request -> {
                            request.setRemoteAddr("0:0:0:0:0:0:0:1");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, internalCallTokenIssuer().authorizationHeader(null)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/internal: no token returns 401")
    default void noTokenReturnsUnauthorized() throws Exception {
        internalChainMockMvc().perform(formats())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: a token signed with another key returns 401")
    default void badSignatureReturnsUnauthorized() throws Exception {
        String foreignToken = new InternalCallTokenIssuer().issue(null);

        internalChainMockMvc().perform(formats().header(HttpHeaders.AUTHORIZATION, bearer(foreignToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: an expired internal-call token returns 401")
    default void expiredTokenReturnsUnauthorized() throws Exception {
        String expired = InternalCallTestTokens.expired(internalCallTokenIssuer());

        internalChainMockMvc().perform(formats().header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: an internal-call token with a wrong iss returns 401")
    default void wrongIssuerReturnsUnauthorized() throws Exception {
        String wrongIssuer = InternalCallTestTokens.wrongIssuer(internalCallTokenIssuer());

        internalChainMockMvc().perform(formats().header(HttpHeaders.AUTHORIZATION, bearer(wrongIssuer)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: a human administrator token of this mode returns 401")
    default void administratorTokenReturnsUnauthorized() throws Exception {
        internalChainMockMvc().perform(formats().header(HttpHeaders.AUTHORIZATION, bearer(administratorToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: Basic credentials return 401")
    default void basicCredentialsReturnUnauthorized() throws Exception {
        internalChainMockMvc().perform(formats()
                        .header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: a valid internal-call token without ROLE_INTERNAL returns 403")
    default void tokenWithoutInternalRoleReturnsForbidden() throws Exception {
        String noInternalRole = InternalCallTestTokens.withRoles(internalCallTokenIssuer(), List.of("ROLE_ADMIN"));

        internalChainMockMvc().perform(formats().header(HttpHeaders.AUTHORIZATION, bearer(noInternalRole)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/internal: a valid internal-call token from a non-loopback peer returns 403, whatever X-Forwarded-For says")
    default void nonLoopbackPeerReturnsForbidden() throws Exception {
        internalChainMockMvc().perform(formats()
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        })
                        .header("X-Forwarded-For", "127.0.0.1")
                        .header("Forwarded", "for=127.0.0.1")
                        .header(HttpHeaders.AUTHORIZATION, internalCallTokenIssuer().authorizationHeader(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/internal: a loopback remote address on a non-loopback local socket returns 403")
    default void loopbackRemoteOnNonLoopbackSocketReturnsForbidden() throws Exception {
        // What a forward-headers strategy produces for a pod-network caller sending X-Forwarded-For: 127.0.0.1.
        internalChainMockMvc().perform(formats()
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            request.setLocalAddr("10.1.2.3");
                            return request;
                        })
                        .header("X-Forwarded-For", "127.0.0.1")
                        .header(HttpHeaders.AUTHORIZATION, internalCallTokenIssuer().authorizationHeader(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/api: an internal-call token returns 401")
    default void internalTokenOnApiReturnsUnauthorized() throws Exception {
        internalChainMockMvc().perform(get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + internalChainDatasetId())
                        .header(HttpHeaders.AUTHORIZATION,
                                internalCallTokenIssuer().authorizationHeader(PROTOCOL_TENANT_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol route: an internal-call token returns 401")
    default void internalTokenOnProtocolRouteReturnsUnauthorized() throws Exception {
        internalChainMockMvc().perform(post("/" + PROTOCOL_TENANT_ID + "/catalog/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE))
                        .header(HttpHeaders.AUTHORIZATION,
                                internalCallTokenIssuer().authorizationHeader(PROTOCOL_TENANT_ID)))
                .andExpect(status().isUnauthorized());
    }
}
