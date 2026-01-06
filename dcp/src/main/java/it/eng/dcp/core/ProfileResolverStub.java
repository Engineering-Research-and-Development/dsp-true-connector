package it.eng.dcp.core;

import it.eng.dcp.common.model.ProfileId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Simple stub resolver.
 * - format "jwt" -> VC11_SL2021_JWT (with or without statusList)
 * - any other format -> VC20_BSSL_JWT (default)
 */
@Service
public class ProfileResolverStub implements ProfileResolver {

    @Override
    public ProfileId resolve(String format, Map<String, Object> attributes) {
        if (format == null) return ProfileId.VC20_BSSL_JWT;
        String f = format.toLowerCase();
        if("VC1_0_JWT".equalsIgnoreCase(f)) {
            f = "jwt";
        }
        // JWT format credentials -> VC11_SL2021_JWT profile
        if ("jwt".equals(f)) {
            return ProfileId.VC11_SL2021_JWT;
        }
        // Any other format -> VC20_BSSL_JWT (default)
        return ProfileId.VC20_BSSL_JWT;
    }
}
