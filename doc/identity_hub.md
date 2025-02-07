# Identity Hub integration

Table of content:

  - [Download and customize to run locally](#download)
  - [Creating participant context](#participant-context)
  - [Get participants](#get-participants)
  - [DID Documents](#did-documents)
  - [Verifiable credentials](#vc)
  - [Dataspace-issuer did document](#issuer-did-document)
  - [Further reading](#more)

Note: informations gathered so far:

## Download and customize to run locally <a name="download"></a>

- Download [identity hub](https://github.com/eclipse-edc/IdentityHub/tree/main); (use released versions v0.10.0 or v.0.11.0)
- Apply following changes (to support super-admin user; removed because security, so far no information how to run and not get 403 on endpoints)

Copy [MinimumViableDataspace-main\extensions\superuser-seed\](https://github.com/eclipse-edc/MinimumViableDataspace/tree/main/extensions/superuser-seed) to IdentityHub-main\extensions\superuser-seed\

```
gradle.properties

version=0.10.0

```

```
‎gradle/libs.versions.toml

edc = "0.10.0"


edc-ih-spi-store = { module = "org.eclipse.edc:identity-hub-store-spi", version.ref = "edc" }

```

```
‎‎launcher/identityhub/build.gradle.kts

dependencies {
    runtimeOnly(project(":dist:bom:identityhub-with-sts-bom"))
    runtimeOnly(project(":extensions:superuser-seed"))   <-- THIS ONE
}
```


```
‎settings.gradle.kts

include(":extensions:superuser-seed")

```

In extensions\superuser-seed\src\main\java\org\eclipse\edc\identityhub\seed\ParticipantContextSeedExtension.java make sure to change line:

```
   .orElse(generatedKey.apiKey());
```

with

```
  .orElse(generatedKey.get("apiKey").toString());
 
```
Build gradle project using

```
gradlew :launcher:identityhub:shadowJar
```


When built, run using following command

```
java -Dweb.http.presentation.port=10001 -Dweb.http.presentation.path="/api/presentation" -Dweb.http.port=8181 -Dweb.http.path="/api" -Dweb.http.identity.port=8182 -Dweb.http.identity.path="/api/identity" -Dedc.ih.api.superuser.key="c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0Cg==" -Dedc.api.accounts.key="password" -jar launcher/identityhub/build/libs/identity-hub.jar

```

Import postman collection from [MVDS project](https://github.com/eclipse-edc/MinimumViableDataspace/blob/main/deployment/postman/MVD.postman_collection.json) and corresponding [environment file.](https://github.com/eclipse-edc/MinimumViableDataspace/blob/main/deployment/postman/MVD%20Local%20Development.postman_environment.json)


## Creating participant context <a name="participant-context"></a>

Following requests are "borrowed" from terraform scripts in MVDS to create consumer and provider participants.

```
curl --location 'http://localhost:8182/api/identity/v1alpha/participants/' \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0Cg==' \
--data '{
           "roles":[],
           "serviceEndpoints":[
             {
                "type": "CredentialService",
                "serviceEndpoint": "http://consumer-identityhub:7082/api/presentation/v1/participants/ZGlkOndlYjpjb25zdW1lci1pZGVudGl0eWh1YiUzQTcwODM6Y29uc3VtZXI=",
                "id": "consumer-credentialservice-1"
             },
             {
                "type": "ProtocolEndpoint",
                "serviceEndpoint": "http://consumer-controlplane:8082/api/dsp",
                "id": "consumer-dsp"
             }
           ],
           "active": true,
           "participantId": "did:web:consumer-identityhub%3A7083:consumer",
           "did": "did:web:consumer-identityhub%3A7083:consumer",
           "key":{
               "keyId": "did:web:consumer-identityhub%3A7083:consumer#key-1",
               "privateKeyAlias": "did:web:consumer-identityhub%3A7083:consumer#key-1",
               "keyGeneratorParams":{
                  "algorithm": "EC"
               }
           }
       }'
       
```

When *c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0Cg==* is Base64 decoded result is: *super-user.super-secret* This value is used as parameter to start identity-hub.jar
If some other value for password is set, be sure to update x-api-key for requests.

Once participants are created, response will contain keys like following:

```
{
    "clientId": "did:web:consumer-identityhub%3A7083:consumer",
    "apiKey": "ZGlkOndlYjpjb25zdW1lci1pZGVudGl0eWh1YiUzQTcwODM6Y29uc3VtZXI=.4lkSnK4tsWGrr6X2hfpuoS1osf+rHBjcTixHV/HqE50i4Dau63uc9NXgzKxe7I4IA/d2W2IrFRVCNqKAqX/h8w==",
    "clientSecret": "tf2VESohQvIgIUYc"
}
```

For now, my guess is that apiKey should be used to access IdentityHub endpoints so that connector can interact with it, but this is not working, not sure why.


## Get participants <a name="get-participants"></a>

```
curl --location 'http://localhost:8182/api/identity/v1alpha/participants' \
--header 'x-api-key: c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0Cg=='

```

Response (extracted only consumer participant):

```
{
    "participantId": "did:web:consumer-identityhub%3A7083:consumer",
    "roles": [],
    "did": "did:web:consumer-identityhub%3A7083:consumer",
    "createdAt": 1738593228734,
    "lastModified": 1738593228734,
    "state": 1,
    "apiTokenAlias": "did:web:consumer-identityhub%3A7083:consumer-apikey"
}
```


## DID Documents <a name="did-documents"></a>

```
curl --location 'http://localhost:8182/api/identity/v1alpha/dids/' \
--header 'x-api-key: c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0Cg=='

```

```
[
	{
		"service": [
			{
				"id": "consumer-dsp",
				"type": "ProtocolEndpoint",
				"serviceEndpoint": "http://consumer-controlplane:8090/"
			},
			{
				"id": "consumer-credentialservice-1",
				"type": "CredentialService",
				"serviceEndpoint": "http://consumer-identityhub:7082/api/presentation/v1/participants/ZGlkOndlYjpjb25zdW1lci1pZGVudGl0eWh1YiUzQTcwODM6Y29uc3VtZXI="
			}
		],
		"verificationMethod": [
			{
				"id": "did:web:consumer-identityhub%3A7083:consumer#key-1",
				"type": "JsonWebKey2020",
				"controller": "did:web:consumer-identityhub%3A7083:consumer",
				"publicKeyMultibase": null,
				"publicKeyJwk": {
					"kty": "EC",
					"use": "sig",
					"crv": "P-256",
					"x": "3ataqHZz-hLpASDWikf0PXQXL4UDp3yWuaTFsFmOlUQ",
					"y": "pq4yR-NDEi-hKr6IgDluLOkLDwLQADgz2PkCwcxVRXI"
				}
			}
		],
		"authentication": [],
		"id": "did:web:consumer-identityhub%3A7083:consumer",
		"@context": [
			"https://www.w3.org/ns/did/v1"
		]
	},
	{
		"service": [],
		"verificationMethod": [
			{
				"id": "super-user-key",
				"type": "JsonWebKey2020",
				"controller": "did:web:super-user",
				"publicKeyMultibase": null,
				"publicKeyJwk": {
					"kty": "OKP",
					"crv": "Ed25519",
					"x": "n07D_O7SkACCag3IDboFkvUml0xtNKl9B_v-r2EV97I"
				}
			}
		],
		"authentication": [],
		"id": "did:web:super-user",
		"@context": [
			"https://www.w3.org/ns/did/v1"
		]
	}
]
```

## Verifiable credentials <a name="vc"></a>

From MVDS:

For now, not sure who and how to create following document/jwt

```
curl --location 'http://localhost:8182/api/identity/v1alpha/credentials' \
--header 'x-api-key: c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0Cg=='
```

```
[
    {
        "participantId": "did:web:consumer-identityhub%3A7083:consumer",
        "id": "40e24588-b510-41ca-966c-c1e0f57d1b15",
        "timestamp": 1700659822500,
        "issuerId": "did:web:dataspace-issuer",
        "holderId": "did:web:consumer-identityhub%3A7083:consumer",
        "state": 500,
        "timeOfLastStatusUpdate": null,
        "issuancePolicy": null,
        "reissuancePolicy": null,
        "verifiableCredential": {
            "rawVc": "eyJraWQiOiJkaWQ6d2ViOmRhdGFzcGFjZS1pc3N1ZXIja2V5LTEiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6d2ViOmRhdGFzcGFjZS1pc3N1ZXIiLCJhdWQiOiJkaWQ6d2ViOmNvbnN1bWVyLWlkZW50aXR5aHViJTNBNzA4MzphbGljZSIsInN1YiI6ImRpZDp3ZWI6Y29uc3VtZXItaWRlbnRpdHlodWIlM0E3MDgzOmFsaWNlIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3czaWQub3JnL3NlY3VyaXR5L3N1aXRlcy9qd3MtMjAyMC92MSIsImh0dHBzOi8vd3d3LnczLm9yZy9ucy9kaWQvdjEiLHsibXZkLWNyZWRlbnRpYWxzIjoiaHR0cHM6Ly93M2lkLm9yZy9tdmQvY3JlZGVudGlhbHMvIiwiY29udHJhY3RWZXJzaW9uIjoibXZkLWNyZWRlbnRpYWxzOmNvbnRyYWN0VmVyc2lvbiIsImxldmVsIjoibXZkLWNyZWRlbnRpYWxzOmxldmVsIn1dLCJpZCI6Imh0dHA6Ly9vcmcueW91cmRhdGFzcGFjZS5jb20vY3JlZGVudGlhbHMvMjM0NyIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJodHRwOi8vb3JnLnlvdXJkYXRhc3BhY2UuY29tI0RhdGFQcm9jZXNzb3JDcmVkZW50aWFsIl0sImlzc3VlciI6ImRpZDp3ZWI6ZGF0YXNwYWNlLWlzc3VlciIsImlzc3VhbmNlRGF0ZSI6IjIwMjMtMDgtMThUMDA6MDA6MDBaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6d2ViOmNvbnN1bWVyLWlkZW50aXR5aHViJTNBNzA4Mzpjb25zdW1lciIsImNvbnRyYWN0VmVyc2lvbiI6IjEuMC4wIiwibGV2ZWwiOiJwcm9jZXNzaW5nIn19LCJpYXQiOjE3MzY5Mzk0NTV9.QNoHRELwvXvJ5msOS_IKCjNVrmF7C41Qf05RwRPEipioy8DWLU2hjodgmncO2b_m4bCTWNphzW0Iny_DIYWWAw",
            "format": "JWT",
            "credential": {
                "credentialSubject": [
                    {
                        "id": null,
                        "claims": {
                            "id": "did:web:consumer-identityhub%3A7083:consumer",
                            "contractVersion": "1.0.0",
                            "level": "processing"
                        }
                    }
                ],
                "id": "http://org.yourdataspace.com/credentials/1235",
                "type": [
                    "VerifiableCredential",
                    "DataProcessorCredential"
                ],
                "issuer": {
                    "id": "did:web:dataspace-issuer",
                    "additionalProperties": {}
                },
                "issuanceDate": "2023-12-12T00:00:00Z",
                "expirationDate": null,
                "credentialStatus": null,
                "description": null,
                "name": null
            }
        }
    },
    {
        "participantId": "did:web:consumer-identityhub%3A7083:consumer",
        "id": "40e24588-b510-41ca-966c-c1e0f57d1b14",
        "timestamp": 1700659822500,
        "issuerId": "did:web:dataspace-issuer",
        "holderId": "did:web:consumer-identityhub%3A7083:consumer",
        "state": 500,
        "timeOfLastStatusUpdate": null,
        "issuancePolicy": null,
        "reissuancePolicy": null,
        "verifiableCredential": {
            "rawVc": "eyJraWQiOiJkaWQ6d2ViOmRhdGFzcGFjZS1pc3N1ZXIja2V5LTEiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6d2ViOmRhdGFzcGFjZS1pc3N1ZXIiLCJhdWQiOiJkaWQ6d2ViOmNvbnN1bWVyLWlkZW50aXR5aHViJTNBNzA4MzphbGljZSIsInN1YiI6ImRpZDp3ZWI6Y29uc3VtZXItaWRlbnRpdHlodWIlM0E3MDgzOmFsaWNlIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3czaWQub3JnL3NlY3VyaXR5L3N1aXRlcy9qd3MtMjAyMC92MSIsImh0dHBzOi8vd3d3LnczLm9yZy9ucy9kaWQvdjEiLHsibXZkLWNyZWRlbnRpYWxzIjoiaHR0cHM6Ly93M2lkLm9yZy9tdmQvY3JlZGVudGlhbHMvIiwibWVtYmVyc2hpcCI6Im12ZC1jcmVkZW50aWFsczptZW1iZXJzaGlwIiwibWVtYmVyc2hpcFR5cGUiOiJtdmQtY3JlZGVudGlhbHM6bWVtYmVyc2hpcFR5cGUiLCJ3ZWJzaXRlIjoibXZkLWNyZWRlbnRpYWxzOndlYnNpdGUiLCJjb250YWN0IjoibXZkLWNyZWRlbnRpYWxzOmNvbnRhY3QiLCJzaW5jZSI6Im12ZC1jcmVkZW50aWFsczpzaW5jZSJ9XSwiaWQiOiJodHRwOi8vb3JnLnlvdXJkYXRhc3BhY2UuY29tL2NyZWRlbnRpYWxzLzIzNDciLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiaHR0cDovL29yZy55b3VyZGF0YXNwYWNlLmNvbSNNZW1iZXJzaGlwQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOiJkaWQ6d2ViOmRhdGFzcGFjZS1pc3N1ZXIiLCJpc3N1YW5jZURhdGUiOiIyMDIzLTA4LTE4VDAwOjAwOjAwWiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOndlYjpjb25zdW1lci1pZGVudGl0eWh1YiUzQTcwODM6Y29uc3VtZXIiLCJtZW1iZXJzaGlwIjp7Im1lbWJlcnNoaXBUeXBlIjoiRnVsbE1lbWJlciIsIndlYnNpdGUiOiJ3d3cud2hhdGV2ZXIuY29tIiwiY29udGFjdCI6ImZpenouYnV6ekB3aGF0ZXZlci5jb20iLCJzaW5jZSI6IjIwMjMtMDEtMDFUMDA6MDA6MDBaIn19fSwiaWF0IjoxNzM2OTM5NDU1fQ.hKy6DrH1LGtPQZncGo9Alz7zv8kIkBDFeinJWjLl2cXmUuxyM8n5jrQKgjBHrJVn7SySNQxIR1k0SKHHVU9QDw",
            "format": "JWT",
            "credential": {
                "credentialSubject": [
                    {
                        "id": "did:web:consumer-identityhub%3A7083:consumer",
                        "claims": {
                            "membershipType": "FullMember",
                            "website": "www.some-other-website.com",
                            "contact": "bar.baz@company.com",
                            "since": "2023-01-01T00:00:00Z"
                        }
                    }
                ],
                "id": "http://org.yourdataspace.com/credentials/2347",
                "type": [
                    "VerifiableCredential",
                    "MembershipCredential"
                ],
                "issuer": {
                    "id": "did:web:dataspace-issuer",
                    "additionalProperties": {}
                },
                "issuanceDate": "2023-12-12T00:00:00Z",
                "expirationDate": null,
                "credentialStatus": null,
                "description": null,
                "name": null
            }
        }
    }
]

```


## Dataspace-issuer did document <a name="issuer-did-document"></a>

Borrowed from MVDS setup - using NginX to static expose did.json for issuer

Add to some existing docker-compose.yml file:

```
  nginx:
    image: nginx:1-alpine
    ports:
      - 80:80
    volumes:
      - ./did.issuer.json:/var/www/.well-known/did.json
      - ./nginx.conf:/etc/nginx/conf.d/default.conf

```

did.issuer.json

```
{"service":[],"verificationMethod":[{"id":"did:web:localhost%3A9876#key-1","type":"JsonWebKey2020","controller":"did:web:localhost%3A9876","publicKeyMultibase":null,"publicKeyJwk":{"kty":"OKP","crv":"Ed25519","x":"Hsq2QXPbbsU7j6JwXstbpxGSgliI04g_fU3z2nwkuVc"}}],"authentication":["key-1"],"id":"did:web:localhost%3A9876","@context":["https://www.w3.org/ns/did/v1",{"@base":"did:web:localhost%3A9876"}]}

```

nginx.conf 

```
server {
    listen 80;
    root /var/www/;
    #index index.html;
}
```

This setup will be used to resolve issued did:web and will resolve to Nginx exposed did.json file, which should be available on
http://localhost/.well-known/did.json

<details>

<summary>Dependencies</summary>

```xml
	  	<!-- this one in tools should we use that one or nimbus
			<dependency>
			<groupId>com.auth0</groupId>
			<artifactId>java-jwt</artifactId>
			<version>4.4.0</version>
		</dependency>
		-->
	  	<dependency>
		    <groupId>com.nimbusds</groupId>
		    <artifactId>nimbus-jose-jwt</artifactId>
		    <version>10.0</version>
		</dependency>
		<dependency>
		<!--	org.bouncycastle.openssl.PEMParser-->
		    <groupId>org.bouncycastle</groupId>
		    <artifactId>bcpkix-jdk18on</artifactId>
		    <version>1.80</version>
		</dependency>
		<dependency>
		    <groupId>org.bouncycastle</groupId>
		    <artifactId>bcprov-jdk16</artifactId>
		    <version>1.46</version>
		</dependency>
		<dependency>
			<!-- Ed25519Signer-->
		    <groupId>com.google.crypto.tink</groupId>
		    <artifactId>tink</artifactId>
		    <version>1.16.0</version>
		</dependency>

```

</details>

<details>

<summary>JWT class for sign and verify</summary>

```code
package it.eng.connector.util;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@ExtendWith(MockitoExtension.class)
public class JwtIdentityHubTest {

	private final ObjectMapper mapper = new ObjectMapper();
	
	 private final JcaPEMKeyConverter pemConverter = new JcaPEMKeyConverter();
	
    public static final String ALGORITHM_RSA = "rsa";
    public static final String ALGORITHM_EC = "ec";
    public static final String ALGORITHM_ECDSA = "ecdsa";
    public static final String ALGORITHM_EDDSA = "eddsa";
    public static final String ALGORITHM_ED25519 = "ed25519";
    
    public static final String ISSUER_PRIVATE_KEY_FILE_PATH = "-----BEGIN PRIVATE KEY-----\r\n"
    		+ "MC4CAQAwBQYDK2VwBCIEID1gMsekH7JN9Q/L2UMCBkAPET10NE0T2BB4c2rRSBzg\r\n"
    		+ "-----END PRIVATE KEY-----";
    public static final String ISSUER_PUBLIC_KEY_FILE_PATH = "-----BEGIN PUBLIC KEY-----\r\n"
    		+ "MCowBQYDK2VwAyEAHsq2QXPbbsU7j6JwXstbpxGSgliI04g/fU3z2nwkuVc=\r\n"
    		+ "-----END PUBLIC KEY-----";

	
	/*
	 * (String rawCredentialFilePath, 
	 * File vcResource, 
	 * String did, 
	 * String issuerDid, 
	 * File issuerDidDocument)
	 * 
	 * 	System.getProperty("user.dir") + "/../../deployment/assets/credentials/k8s/provider/membership_vc.json",
    	new File(System.getProperty("user.dir") + "/../../deployment/assets/credentials/k8s/provider/membership-credential.json"),
        "did:web:provider-identityhub%3A7083:bob", 
        DATASPACE_ISSUER_DID_K8S, 
        ISSUER_DID_DOCUMENT_K8S)
	 * 
	 */
	@Test
	public void geenrateJwt() throws StreamReadException, DatabindException, IOException, JOSEException {
		String issuerDid = "did:web:dataspace-issuer";
		String did = "did:web:consumer-identityhub%3A7083:alice";
		
		String rawCredentialFilePath = "{\r\n"
				+ "  \"@context\": [\r\n"
				+ "    \"https://www.w3.org/2018/credentials/v1\",\r\n"
				+ "    \"https://w3id.org/security/suites/jws-2020/v1\",\r\n"
				+ "    \"https://www.w3.org/ns/did/v1\",\r\n"
				+ "    {\r\n"
				+ "      \"mvd-credentials\": \"https://w3id.org/mvd/credentials/\",\r\n"
				+ "      \"contractVersion\": \"mvd-credentials:contractVersion\",\r\n"
				+ "      \"level\": \"mvd-credentials:level\"\r\n"
				+ "    }\r\n"
				+ "  ],\r\n"
				+ "  \"id\": \"http://org.yourdataspace.com/credentials/2347\",\r\n"
				+ "  \"type\": [\r\n"
				+ "    \"VerifiableCredential\",\r\n"
				+ "    \"http://org.yourdataspace.com#DataProcessorCredential\"\r\n"
				+ "  ],\r\n"
				+ "  \"issuer\": \"did:web:dataspace-issuer\",\r\n"
				+ "  \"issuanceDate\": \"2023-08-18T00:00:00Z\",\r\n"
				+ "  \"credentialSubject\": {\r\n"
				+ "    \"id\": \"did:web:consumer-identityhub%3A7083:consumer\",\r\n"
				+ "    \"contractVersion\": \"1.0.0\",\r\n"
				+ "    \"level\": \"processing\"\r\n"
				+ "  }\r\n"
				+ "}";
		
		
		var header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .keyID(issuerDid + "#key-1")
                .type(JOSEObjectType.JWT)
                .build();

		var credential = mapper.readValue(rawCredentialFilePath, Map.class);

        var claims = new JWTClaimsSet.Builder()
                .audience(did)
                .subject(did)
                .issuer(issuerDid)
                .claim("vc", credential)
                .issueTime(Date.from(Instant.now()))
                .build();
	
        List<KeyPair> keyPairList = parseKeys(ISSUER_PRIVATE_KEY_FILE_PATH);
        PrivateKey privateKey = (PrivateKey) keyPairList
                .stream()
                .filter(Objects::nonNull) // PEM strings that only contain public keys would get eliminated here
                .map(keyPair -> keyPair.getPrivate() != null ? keyPair.getPrivate() : keyPair.getPublic())
                .findFirst()
                .orElseGet(() -> null);
        
        
        List<KeyPair> keyPairListPublic =  parseKeys(ISSUER_PUBLIC_KEY_FILE_PATH);
        PublicKey publicKey = (PublicKey) keyPairListPublic
                .stream()
                .filter(Objects::nonNull) // PEM strings that only contain public keys would get eliminated here
                .map(keyPair -> keyPair.getPrivate() != null ? keyPair.getPrivate() : keyPair.getPublic())
                .findFirst()
                .orElseGet(() -> null); ;

        // sign raw credentials with new issuer public key
        var jwt = new SignedJWT(header, claims);
        JWSSigner signer = createSignerFor(privateKey);
        
        jwt.sign(signer);
        System.out.println(jwt.serialize());
        
        JWSVerifier verifier = createVerifierFor(publicKey);
        boolean validJwt = jwt.verify(verifier);
        System.out.println(validJwt);
        
	}
	
	 public static JWSSigner createSignerFor(PrivateKey key) {
	        var algorithm = key.getAlgorithm().toLowerCase();
	        try {
	            return switch (algorithm) {
	                case ALGORITHM_EC, ALGORITHM_ECDSA -> getEcdsaSigner((ECPrivateKey) key);
	                case ALGORITHM_RSA -> new RSASSASigner(key);
	                case ALGORITHM_EDDSA, ALGORITHM_ED25519 -> createEdDsaVerifier(key);
	                default -> throw new IllegalArgumentException("Algorithm " + algorithm + " not supported");
	            };
	        } catch (JOSEException ex) {
	            throw new RuntimeException(ex);
	        }
	    }
	 
	 public static JWSVerifier createVerifierFor(PublicKey publicKey) throws JOSEException {
	        var algorithm = publicKey.getAlgorithm().toLowerCase();
	        try {
	            return switch (algorithm) {
	                case ALGORITHM_EC, ALGORITHM_ECDSA -> getEcdsaVerifier((ECPublicKey) publicKey);
	                case ALGORITHM_RSA -> new RSASSAVerifier((RSAPublicKey) publicKey);
	                case ALGORITHM_EDDSA, ALGORITHM_ED25519 -> createEdDsaVerifier(publicKey);
	                default -> throw new IllegalArgumentException("Not supported algorithm " + algorithm);
	            };
	        } catch (JOSEException e) {
	            throw e;
	        }
	    }
	 
	    private static ECDSAVerifier getEcdsaVerifier(ECPublicKey publicKey) throws JOSEException {
	        var verifier = new ECDSAVerifier(publicKey);
	        verifier.getJCAContext().setProvider(BouncyCastleProviderSingleton.getInstance());
	        return verifier;
	    }
	 
	  private static ECDSASigner getEcdsaSigner(ECPrivateKey key) throws JOSEException {
	        var signer = new ECDSASigner(key);
	        signer.getJCAContext().setProvider(BouncyCastleProviderSingleton.getInstance());
	        return signer;
	    }
	  
	  private static Ed25519Verifier createEdDsaVerifier(PublicKey publicKey) throws JOSEException {
	        var edKey = (EdECPublicKey) publicKey;
	        var curve = getCurveAllowing(edKey, ALGORITHM_ED25519);


	        var urlX = encodeX(edKey.getPoint());
	        var okp = new OctetKeyPair.Builder(curve, urlX)
	                .build();
	        return new Ed25519Verifier(okp);

	    }

	  private static Curve getCurveAllowing(EdECKey edKey, String... allowedCurves) {
	        var curveName = edKey.getParams().getName();

	        if (!Arrays.asList(allowedCurves).contains(curveName.toLowerCase())) {
	            throw new IllegalArgumentException("Unsupported curve: %s. Only the following curves is supported: %s.".formatted(curveName, String.join(",", allowedCurves)));
	        }
	        return Curve.parse(curveName);
	    }
	  
	  private static Base64URL encodeX(EdECPoint point) {
	        var bytes = reverseArray(point.getY().toByteArray());

	        // when the X-coordinate of the curve is odd, we flip the highest-order bit of the first (or last, since we reversed) byte
	        if (point.isXOdd()) {
	            var mask = (byte) 128; // is 1000 0000 binary
	            bytes[bytes.length - 1] ^= mask; // XOR means toggle the left-most bit
	        }

	        return Base64URL.encode(bytes);
	    }
	  
	   private static byte[] reverseArray(byte[] array) {
	        for (var i = 0; i < array.length / 2; i++) {
	            var temp = array[i];
	            array[i] = array[array.length - 1 - i];
	            array[array.length - 1 - i] = temp;
	        }
	        return array;
	    }
	   
	   private static Ed25519Signer createEdDsaVerifier(PrivateKey key) throws JOSEException {
	        var edKey = (EdECPrivateKey) key;
	        var curve = getCurveAllowing(edKey, ALGORITHM_ED25519);


	        var urlX = Base64URL.encode(new byte[0]);
	        var urlD = encodeD(edKey);

	        // technically, urlX should be the public bytes (i.e. public key), but we don't have that here, and we don't need it.
	        // that is because internally, the Ed25519Signer only wraps the Ed25519Sign class from the Tink library, using only the private bytes ("d")
	        var octetKeyPair = new OctetKeyPair.Builder(curve, urlX)
	                .d(urlD)
	                .build();
	        return new Ed25519Signer(octetKeyPair);
	    }
	   
	   private static Base64URL encodeD(EdECPrivateKey edKey) {
	        var bytes = edKey.getBytes().orElseThrow(() -> new RuntimeException("Private key is not willing to disclose its bytes"));
	        return Base64URL.encode(bytes);
	    }
	   
	   
	   // Key handling
	   
	   private List<KeyPair> parseKeys(String pemEncodedKeys) {

	        // Strips the "---- {BEGIN,END} {CERTIFICATE,PUBLIC/PRIVATE KEY} -----"-like header and footer lines,
	        // base64-decodes the body,
	        // then uses the proper key specification format to turn it into a JCA Key instance
	        var pemReader = new StringReader(pemEncodedKeys);
	        var parser = new PEMParser(pemReader);
	        var keys = new ArrayList<KeyPair>();

	        try {
	            Object pemObj;
	            do {
	                pemObj = parser.readObject();
	                if (pemObj instanceof SubjectPublicKeyInfo subjectPublicKeyInfo) { // if public key, use as-is
	                    keys.add(toKeyPair(subjectPublicKeyInfo));
	                } else if (pemObj instanceof X509CertificateHolder x509CertificateHolder) { // if it's a certificate, use the public key which is signed
	                    keys.add(toKeyPair(x509CertificateHolder));
	                } else if (pemObj instanceof PEMKeyPair pemKeyPair) { // if private key is given in DER format
	                    keys.add(toKeyPair(pemKeyPair));
	                } else if (pemObj instanceof PrivateKeyInfo privateKeyInfo) { // if (RSA) private key is given in PKCS8 format
	                    keys.add(toKeyPair(privateKeyInfo));
	                }
	            } while (pemObj != null);

	            return keys;
	        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
	            return null;
	        }
	    }
	   
	   private KeyPair toKeyPair(SubjectPublicKeyInfo spki) throws PEMException {
	        return new KeyPair(pemConverter.getPublicKey(spki), null);
	    }

	    private KeyPair toKeyPair(X509CertificateHolder pemObj) throws PEMException {
	        var spki = pemObj.getSubjectPublicKeyInfo();
	        return new KeyPair(pemConverter.getPublicKey(spki), null);
	    }

	    private KeyPair toKeyPair(PEMKeyPair pair) throws PEMException {
	        return pemConverter.getKeyPair(pair);
	    }
	    
	    private KeyPair toKeyPair(PrivateKeyInfo pki) throws PEMException, NoSuchAlgorithmException, InvalidKeySpecException {
	        var privateKey = pemConverter.getPrivateKey(pki);

	        // If it's RSA, we can use the modulus and public exponents as BigIntegers to create a public key
	        if (privateKey instanceof RSAPrivateCrtKey rsaPrivateCrtKey) {
	            var publicKeySpec = new RSAPublicKeySpec((rsaPrivateCrtKey).getModulus(), (rsaPrivateCrtKey.getPublicExponent()));

	            var keyFactory = KeyFactory.getInstance("RSA");
	            var publicKey = keyFactory.generatePublic(publicKeySpec);
	            return new KeyPair(publicKey, privateKey);
	        }

	        // If was a private EC key, it would already have been received as a PEMKeyPair
	        return new KeyPair(null, privateKey);
	    }
}

```

</details>


## Further reading <a name="more"></a>

[Decentralized Claims Protocol](https://github.com/eclipse-dataspace-dcp/decentralized-claims-protocol/tree/main)

[Identity Hub EDC](https://eclipse-edc.github.io/documentation/for-adopters/identity-hub/)
