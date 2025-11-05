package it.eng.dcp.core;

import it.eng.dcp.model.ProfileId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Simple stub resolver.
 * - format "jwt" with statusList -> VC11_SL2021_JWT
 * - format "json-ld" without statusList -> VC11_SL2021_JSONLD
 * - otherwise null
 */
@Service
public class ProfileResolverStub implements ProfileResolver {

    @Override
    public ProfileId resolve(String format, Map<String, Object> attributes) {
        if (format == null) return null;
        String f = format.toLowerCase();
        boolean hasStatusList = attributes != null && attributes.containsKey("statusList") && attributes.get("statusList") != null;
        if ("jwt".equals(f) && hasStatusList) return ProfileId.VC11_SL2021_JWT;
        if ("json-ld".equals(f) && !hasStatusList) return ProfileId.VC11_SL2021_JSONLD;
        return null;
    }
}
