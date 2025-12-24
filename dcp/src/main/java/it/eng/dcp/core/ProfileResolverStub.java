package it.eng.dcp.core;

import it.eng.dcp.common.model.ProfileId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Simple stub resolver.
 * - format "jwt" -> VC11_SL2021_JWT (with or without statusList)
 * - format "json-ld" -> VC11_SL2021_JSONLD (with or without statusList)
 * - otherwise null
 */
@Service
public class ProfileResolverStub implements ProfileResolver {

    @Override
    public ProfileId resolve(String format, Map<String, Object> attributes) {
        if (format == null) return null;
        String f = format.toLowerCase();
        if("VC1_0_JWT".equalsIgnoreCase(f)) {
            f = "jwt";
        } else if("VC1_0_JSONLD".equalsIgnoreCase(f)) {
            f = "json-ld";
        }
        // JWT format credentials -> VC11_SL2021_JWT profile
        if ("jwt".equals(f)) {
            return ProfileId.VC11_SL2021_JWT;
        }

        // JSON-LD format credentials -> VC11_SL2021_JSONLD profile
        if ("json-ld".equals(f)) {
            return ProfileId.VC11_SL2021_JSONLD;
        }

        return null;
    }
}
