package it.eng.tools.daps;

import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Enumeration;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DapsCertificateProviderOmejdn {

	private String targetAudience = "idsc:IDS_CONNECTORS_ALL";

	private DapsProperties dapsProperties;
	private SslBundles sslBundles;

	private KeyStore dapsKeystore;

	public DapsCertificateProviderOmejdn(DapsProperties dapsProperties, SslBundles sslBundles) {
		this.dapsProperties = dapsProperties;
		this.sslBundles = sslBundles;
	}

	@PostConstruct
	private void loadKeystore() {
		if (dapsProperties.isEnabledDapsInteraction()) {
			Enumeration<String> aliases;
			try {
				dapsKeystore = sslBundles.getBundle("daps").getStores().getKeyStore();
				aliases = sslBundles.getBundle("daps").getStores().getKeyStore().aliases();
				while(aliases.hasMoreElements()) {
					String alias = aliases.nextElement();
					checkCertificateExired(dapsKeystore.getCertificate(alias));
				}
			} catch (KeyStoreException | NoSuchSslBundleException | CertificateException e) {
				log.error(e.getLocalizedMessage());
			}
		} else {
			log.info("**********************************************************************");
			log.info("DAPS Interaction disabled. DAPS KeyStore not loaded");
			log.info("**********************************************************************");
		}
	}

	public String getDapsV2Jws() {
		log.debug("V2");

		// create signed JWT (JWS)
		// Create expiry date one day (86400 seconds) from now
		Date expiryDate = Date.from(Instant.now().plusSeconds(86400));
		String jws = null;
		try {
			String connectorUUID = getConnectorUUID();
			Algorithm algorithm = Algorithm.RSA256(null, (RSAPrivateKey) getPrivateKey());
			jws = JWT.create().withIssuer(connectorUUID).withSubject(connectorUUID)
					.withClaim("@context", "https://w3id.org/idsa/contexts/context.jsonld")
					.withClaim("@type", "ids:DatRequestToken")
					.withExpiresAt(expiryDate)
					.withIssuedAt(Date.from(Instant.now()))
					.withAudience(targetAudience)
					.withNotBefore(Date.from(Instant.now()))
					.sign(algorithm);
		} catch (JWTCreationException | KeyStoreException exception) {
			log.error("Token creation error: {}", exception.getMessage());
		}
		return jws;
	}

	private Key getPrivateKey() {
		try {
			return dapsKeystore.getKey(sslBundles.getBundle("daps").getKey().getAlias(), sslBundles.getBundle("daps").getStores().getKeyStorePassword().toCharArray());
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
			log.error("Error while trying to get private key from keystore, {}", e);
		}
		return null;
	}

	private String getConnectorUUID() throws KeyStoreException {
		String alias = sslBundles.getBundle("daps").getKey().getAlias();
		// Get AKI
		// GET 2.5.29.14 SubjectKeyIdentifier / 2.5.29.35 AuthorityKeyIdentifier
		String aki_oid = Extension.authorityKeyIdentifier.getId();
		byte[] rawAuthorityKeyIdentifier = ((X509Certificate) dapsKeystore.getCertificate(alias)).getExtensionValue(aki_oid);
		ASN1OctetString akiOc = ASN1OctetString.getInstance(rawAuthorityKeyIdentifier);
		AuthorityKeyIdentifier aki = AuthorityKeyIdentifier.getInstance(akiOc.getOctets());
		byte[] authorityKeyIdentifier = aki.getKeyIdentifier();

		// GET SKI
		String ski_oid = Extension.subjectKeyIdentifier.getId();
		byte[] rawSubjectKeyIdentifier = ((X509Certificate) dapsKeystore.getCertificate(alias)).getExtensionValue(ski_oid);
		ASN1OctetString ski0c = ASN1OctetString.getInstance(rawSubjectKeyIdentifier);
		SubjectKeyIdentifier ski = SubjectKeyIdentifier.getInstance(ski0c.getOctets());
		byte[] subjectKeyIdentifier = ski.getKeyIdentifier();

		String aki_result = beautifyHex(encodeHexString(authorityKeyIdentifier).toUpperCase());
		String ski_result = beautifyHex(encodeHexString(subjectKeyIdentifier).toUpperCase());

		String connectorUUID = ski_result + "keyid:" + aki_result.substring(0, aki_result.length() - 1);

		return connectorUUID;
	}

	/**
	 * Encode a byte array to an hex string.
	 * 
	 * @param byteArray
	 * @return encode hex string
	 */
	private String encodeHexString(byte[] byteArray) {
		StringBuffer hexStringBuffer = new StringBuffer();
		for (int i = 0; i < byteArray.length; i++) {
			hexStringBuffer.append(byteToHex(byteArray[i]));
		}
		return hexStringBuffer.toString();
	}

	/**
	 * Convert byte array to hex without any dependencies to libraries.
	 * 
	 * @param num
	 * @return Hexa string representation of bytes
	 */
	private String byteToHex(byte num) {
		char[] hexDigits = new char[2];
		hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
		hexDigits[1] = Character.forDigit((num & 0xF), 16);
		return new String(hexDigits);
	}

	/***
	 * Beautyfies Hex strings and will generate a result later used to create the.
	 * client id (XX:YY:ZZ)
	 * 
	 * @param hexString HexString to be beautified
	 * @return beautifiedHex result
	 */
	private String beautifyHex(String hexString) {
		String[] splitString = split(hexString, 2);
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < splitString.length; i++) {
			sb.append(splitString[i]);
			sb.append(":");
		}
		return sb.toString();
	}

	/***
	 * Split string ever len chars and return string array.
	 * @param src
	 * @param len
	 * @return array of strings splitted
	 */
	private String[] split(String src, int len) {
		String[] result = new String[(int)Math.ceil((double)src.length()/(double)len)];
		for (int i=0; i<result.length; i++)
			result[i] = src.substring(i*len, Math.min(src.length(), (i+1)*len));
		return result;
	}

	private void checkCertificateExired(Certificate certificate) throws CertificateException {
		if (certificate instanceof X509Certificate) {
			X509Certificate x509 = (X509Certificate) certificate;
			try {
				x509.checkValidity();
			} catch (CertificateExpiredException | CertificateNotYetValidException e) {
				throw new CertificateException("Certificate expired");
			}
		}
		log.info("DAPS certificate still valid");
	}
}
