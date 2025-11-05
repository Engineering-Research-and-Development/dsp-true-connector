package it.eng.dsp.model;

import it.eng.dcp.service.KeyMetadataService;
import it.eng.dcp.service.KeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Objects;

import static java.lang.String.format;

@ExtendWith(MockitoExtension.class)
public class DCPPlayground {

    @Mock
    private KeyMetadataService keyMetadataService;
    @InjectMocks
    private KeyService ks;

    String address = "http://localhost:8083";
    String verifierDid = null;//"verifierDid";

    @Test
    public void resolveDid() {
        this.verifierDid = Objects.requireNonNullElseGet(verifierDid, () -> parseDid("verifier"));
        System.out.println(this.verifierDid);
    }

    @Test
    public void generateKey() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyPair keyPair = ks.getKeyPair();
        System.out.println(keyPair);
        System.out.println(ks.convertPublicKeyToJWK());
        System.out.println(ks.generateEcKey().toECKey());
    }

    @Test
    public void loadKSFromFile() {
        KeyPair keyPair = ks.loadKeyPairFromP12("eckey.p12", "password", "dsptrueconnector");
        System.out.println(keyPair);
        System.out.println(ks.convertPublicKeyToJWK());
    }

    private String parseDid(String discriminator) {
        var uri = URI.create(address);
        return uri.getPort() != 443 ? format("did:web:%s%%3A%s:%s", uri.getHost(), uri.getPort(), discriminator)
                : format("did:web:%s:%s", uri.getHost(), discriminator);
    }

    @Test
    public void printujJson() {
        String name = "John";
        int age = 30;
        String city = "New York";
        String json = """
                {
                  "name": "%s",
                  "age": %d,
                  "city": "%s"
                  "address" : "via roma 10"
                }
                """.formatted(name, age, city);
        System.out.println(json);
    }
}
