package it.eng.tools.daps;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

public class DapsUtils {
	
	public static RSAPublicKey publicKey;
	public static RSAPrivateKey privateKey;
	
	static {
		KeyPairGenerator kpg;
		try {
			kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			KeyPair kp = kpg.generateKeyPair();
			publicKey = (RSAPublicKey) kp.getPublic();
			privateKey = (RSAPrivateKey) kp.getPrivate();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	public static String createTestToken() {
//		JwkProvider provider = new UrlJwkProvider(ClassLoader.getSystemClassLoader().getResource("jwks-single.json"));
//		Jwk jwk = provider.get("NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg");
		Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) publicKey, privateKey);
		return JWT.create()
				.withIssuer("Test")
				.withSubject("Test Details")
				.withClaim("userId", "1234")
				.withIssuedAt(new Date())
				.withKeyId("NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg")
				.withExpiresAt(new Date(System.currentTimeMillis() + 5000L))
				.withJWTId(UUID.randomUUID().toString())
				.withNotBefore(new Date(System.currentTimeMillis() + 1000L))
				.sign(algorithm);
	}
}
