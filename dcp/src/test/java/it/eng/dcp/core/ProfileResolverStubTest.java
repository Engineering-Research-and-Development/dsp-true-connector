package it.eng.dcp.core;

import it.eng.dcp.common.model.ProfileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileResolverStubTest {

    private final ProfileResolverStub resolver = new ProfileResolverStub();

    @Test
    @DisplayName("JWT with statusList resolves to VC11_SL2021_JWT")
    void jwtWithStatusListResolvesToJwtProfile() {
        ProfileId p = resolver.resolve("jwt", Map.of("statusList", "https://example.com/sl"));
        assertEquals(ProfileId.VC11_SL2021_JWT, p);
    }

    @Test
    @DisplayName("unsupported format returns null")
    void unsupportedFormatReturnsNull() {
        assertNull(resolver.resolve("xml", Map.of()));
    }
}

