package it.eng.dcp.issuer.service.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.model.StatusList;
import it.eng.dcp.issuer.service.IssuerIdentity;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * Signs status list credentials in the format of the list's profile (DCP v1.0.1 §6.10 and Appendix A).
 *
 * <ul>
 *   <li>{@code vc11-sl2021/jwt}: a VC Data Model 1.1 {@code StatusList2021Credential} in the {@code vc} claim;
 *       {@code encodedList} is the base64url (no padding) GZIP-compressed bitstring.</li>
 *   <li>{@code vc20-bssl/jwt}: a VC Data Model 2.0 {@code BitstringStatusListCredential} as JWT claims;
 *       {@code encodedList} is the multibase ({@code u} prefix) base64url (no padding) GZIP-compressed
 *       bitstring.</li>
 * </ul>
 *
 * <p>The credential is signed with the Issuer's current signing key and carries no holder data.
 */
@Component
public class StatusListCredentialGenerator {

    /** Media type of a VC 1.1 JWT status list credential. */
    public static final String VC11_MEDIA_TYPE = "application/jwt";

    /** Media type of a VC 2.0 JWT status list credential, matching the {@code typ} header of issued VC 2.0 JWTs. */
    public static final String VC20_MEDIA_TYPE = "application/vc+ld+jwt";

    private static final String VC11_CONTEXT = "https://www.w3.org/2018/credentials/v1";
    private static final String STATUS_LIST_2021_CONTEXT = "https://w3id.org/vc/status-list/2021/v1";
    private static final String VC20_CONTEXT = "https://www.w3.org/ns/credentials/v2";
    private static final String VERIFIABLE_CREDENTIAL = "VerifiableCredential";
    private static final String MULTIBASE_BASE64URL_PREFIX = "u";
    private static final String SUBJECT_FRAGMENT = "#list";

    private final IssuerIdentity issuerIdentity;

    /**
     * Creates the generator.
     *
     * @param issuerIdentity the single Issuer identity, which holds its signing key
     */
    public StatusListCredentialGenerator(IssuerIdentity issuerIdentity) {
        this.issuerIdentity = issuerIdentity;
    }

    /**
     * Builds and signs the status list credential of a list.
     *
     * @param statusList the status list
     * @return the compact JWT of the signed status list credential
     * @throws IllegalStateException if signing fails
     */
    public String generate(StatusList statusList) {
        Objects.requireNonNull(statusList, "statusList");
        ECKey signingKey = issuerIdentity.signingKey();
        Instant now = Instant.now();
        boolean vc20 = statusList.getProfile() == ProfileId.VC20_BSSL_JWT;

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("id", statusList.getUrl() + SUBJECT_FRAGMENT);
        subject.put("type", statusList.getProfile().getStatusListType());
        subject.put("statusPurpose", statusList.getStatusPurpose());
        subject.put("encodedList", encodeList(statusList.getBitstring(), statusList.getProfile()));

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(statusList.getIssuerDid())
                .subject(statusList.getUrl() + SUBJECT_FRAGMENT)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .jwtID(statusList.getUrl());
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.getKeyID());

        if (vc20) {
            claims.claim("@context", List.of(VC20_CONTEXT))
                    .claim("id", statusList.getUrl())
                    .claim("type", List.of(VERIFIABLE_CREDENTIAL, "BitstringStatusListCredential"))
                    .claim("issuer", Map.of("id", statusList.getIssuerDid()))
                    .claim("validFrom", now.toString())
                    .claim("credentialSubject", subject);
            header.type(new JOSEObjectType("vc+ld+jwt"));
        } else {
            Map<String, Object> vc = new LinkedHashMap<>();
            vc.put("@context", List.of(VC11_CONTEXT, STATUS_LIST_2021_CONTEXT));
            vc.put("id", statusList.getUrl());
            vc.put("type", List.of(VERIFIABLE_CREDENTIAL, "StatusList2021Credential"));
            vc.put("issuer", statusList.getIssuerDid());
            vc.put("issuanceDate", now.toString());
            vc.put("credentialSubject", subject);
            claims.claim("vc", vc);
            header.type(JOSEObjectType.JWT);
        }

        try {
            SignedJWT jwt = new SignedJWT(header.build(), claims.build());
            jwt.sign(new ECDSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign status list credential " + statusList.getId(), e);
        }
    }

    /**
     * Tells whether a signed status list credential was signed with the Issuer's current signing key.
     *
     * @param signedCredential the compact JWT, may be null
     * @return true when the JWT parses and its {@code kid} is the current signing key ID
     */
    public boolean isSignedWithCurrentKey(String signedCredential) {
        if (signedCredential == null || signedCredential.isBlank()) {
            return false;
        }
        try {
            String kid = SignedJWT.parse(signedCredential).getHeader().getKeyID();
            return Objects.equals(kid, issuerIdentity.signingKey().getKeyID());
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Returns the media type the status list credential of a profile is served with.
     *
     * @param profile the credential profile
     * @return the media type
     */
    public static String mediaTypeOf(ProfileId profile) {
        return profile == ProfileId.VC20_BSSL_JWT ? VC20_MEDIA_TYPE : VC11_MEDIA_TYPE;
    }

    /**
     * Encodes a bitstring as the {@code encodedList} of the profile's status list format.
     *
     * @param bitstring the status bits, index 0 at the left-most bit of the first byte
     * @param profile the credential profile
     * @return the encoded list
     */
    static String encodeList(byte[] bitstring, ProfileId profile) {
        String base64url = Base64.getUrlEncoder().withoutPadding().encodeToString(gzip(bitstring));
        return profile == ProfileId.VC20_BSSL_JWT ? MULTIBASE_BASE64URL_PREFIX + base64url : base64url;
    }

    private static byte[] gzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress the status list", e);
        }
        return out.toByteArray();
    }
}
