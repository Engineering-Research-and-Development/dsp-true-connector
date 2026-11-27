package it.eng.dcp.spi.testkit;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.ParticipantKeyProvider.PublishedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract every {@link ParticipantKeyProvider} must honour. A host subclasses it, supplies the fixtures
 * and inherits the tests.
 *
 * <p>The tests rotate keys, so the fixtures must be fresh for every test: create the implementation and
 * its key storage in a {@code @BeforeEach} method of the subclass instead of sharing them statically.
 */
public abstract class ParticipantKeyProviderContractTest {

    private static final int P256_FIELD_SIZE = 256;

    /**
     * Returns the implementation under test.
     *
     * @return the key provider
     */
    protected abstract ParticipantKeyProvider keyProvider();

    /**
     * Returns an enabled participant the key provider serves.
     *
     * @return the first participant
     */
    protected abstract ParticipantContext participantA();

    /**
     * Returns a second enabled participant with its own keys. Only called when
     * {@link #supportsMultipleParticipants()} is {@code true}.
     *
     * @return the second participant
     */
    protected abstract ParticipantContext participantB();

    /**
     * Declares whether the implementation serves more than one participant. The multi-participant cases are
     * skipped when it does not.
     *
     * @return {@code true} if the implementation serves more than one participant
     */
    protected abstract boolean supportsMultipleParticipants();

    /**
     * Makes the grace period of every key retired so far elapse, for example by moving the clock or the
     * retirement timestamps of the stored keys, so that the contract does not have to wait in real time.
     */
    protected abstract void expireKeyGracePeriod();

    @Test
    @DisplayName("the active key signs, and the published active key verifies the signature")
    void activeKeySigns() throws JOSEException {
        ParticipantContext participant = participantA();

        ECKey signingKey = keyProvider().activeSigningKey(participant);
        List<PublishedKey> published = keyProvider().publishedKeys(participant);

        assertThat(signingKey.isPrivate()).isTrue();
        assertThat(published).isNotEmpty();
        assertThat(published.get(0).active()).isTrue();
        JWSObject jws = sign(signingKey);
        assertThat(jws.verify(new ECDSAVerifier(toEcKey(published.get(0))))).isTrue();
    }

    @Test
    @DisplayName("after rotation the new key signs and the previous public key stays published")
    void previousKeyStaysPublishedAfterRotation() {
        ParticipantContext participant = participantA();
        PublishedKey before = keyProvider().publishedKeys(participant).get(0);

        String newAlias = keyProvider().rotate(participant);

        List<PublishedKey> after = keyProvider().publishedKeys(participant);
        assertThat(newAlias).isNotBlank();
        assertThat(after.get(0).active()).isTrue();
        assertThat(after.get(0).kid()).isNotEqualTo(before.kid());
        assertThat(publicPoint(keyProvider().activeSigningKey(participant))).isEqualTo(publicPoint(toEcKey(after.get(0))));
        assertThat(after).anySatisfy(key -> {
            assertThat(key.kid()).isEqualTo(before.kid());
            assertThat(key.active()).isFalse();
        });
    }

    @Test
    @DisplayName("once the grace period has elapsed the previous public key is removed")
    void previousKeyIsRemovedAfterGracePeriod() throws JOSEException {
        ParticipantContext participant = participantA();
        PublishedKey before = keyProvider().publishedKeys(participant).get(0);
        keyProvider().rotate(participant);

        expireKeyGracePeriod();

        List<PublishedKey> after = keyProvider().publishedKeys(participant);
        assertThat(after).hasSize(1);
        assertThat(after.get(0).active()).isTrue();
        assertThat(after.get(0).kid()).isNotEqualTo(before.kid());
        assertThat(sign(keyProvider().activeSigningKey(participant))
                .verify(new ECDSAVerifier(toEcKey(after.get(0))))).isTrue();
    }

    @Test
    @DisplayName("keys of different participants are never shared")
    void keysAreNeverSharedBetweenParticipants() {
        assumeTrue(supportsMultipleParticipants(), "the implementation serves a single participant");
        ParticipantContext a = participantA();
        ParticipantContext b = participantB();

        ECKey signingKeyA = keyProvider().activeSigningKey(a);
        ECKey signingKeyB = keyProvider().activeSigningKey(b);
        Set<String> kidsB = kids(b);

        assertThat(publicPoint(signingKeyA)).isNotEqualTo(publicPoint(signingKeyB));
        assertThat(kids(a)).doesNotContainAnyElementsOf(kidsB);

        keyProvider().rotate(a);

        assertThat(publicPoint(keyProvider().activeSigningKey(b))).isEqualTo(publicPoint(signingKeyB));
        assertThat(kids(b)).isEqualTo(kidsB);
        assertThat(kids(a)).doesNotContainAnyElementsOf(kidsB);
    }

    private Set<String> kids(ParticipantContext participant) {
        return keyProvider().publishedKeys(participant).stream().map(PublishedKey::kid).collect(Collectors.toSet());
    }

    private static List<Base64URL> publicPoint(ECKey key) {
        return List.of(key.getX(), key.getY());
    }

    private static JWSObject sign(ECKey signingKey) throws JOSEException {
        JWSObject jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.getKeyID()).build(),
                new Payload("contract-test"));
        jws.sign(new ECDSASigner(signingKey));
        return jws;
    }

    private static ECKey toEcKey(PublishedKey key) {
        return new ECKey.Builder(Curve.P_256, coordinate(key, "x"), coordinate(key, "y")).build();
    }

    private static Base64URL coordinate(PublishedKey key, String name) {
        BigInteger value = new Base64URL(String.valueOf(key.jwk().get(name))).decodeToBigInteger();
        return ECKey.encodeCoordinate(P256_FIELD_SIZE, value);
    }
}
