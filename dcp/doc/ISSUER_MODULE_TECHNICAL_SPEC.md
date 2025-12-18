# DCP Issuer Module - Technical Specification

## Overview

This document provides detailed technical specifications for the standalone DCP Issuer module, including code examples, configurations, and implementation details.

## Module Dependencies

### `dcp-common/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>it.eng</groupId>
        <artifactId>trueconnector</artifactId>
        <version>${revision}</version>
    </parent>
    
    <artifactId>dcp-common</artifactId>
    <name>DCP Common Library</name>
    <description>Shared models and utilities for DCP modules</description>
    
    <dependencies>
        <!-- Jackson for JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>2.17.1</version>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Nimbus JOSE + JWT -->
        <dependency>
            <groupId>com.nimbusds</groupId>
            <artifactId>nimbus-jose-jwt</artifactId>
            <version>10.5</version>
        </dependency>
        
        <!-- Spring Data MongoDB (for annotations only) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
            <scope>provided</scope>
        </dependency>
        
        <!-- Test dependencies -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### `dcp-issuer/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>it.eng</groupId>
        <artifactId>trueconnector</artifactId>
        <version>${revision}</version>
    </parent>
    
    <artifactId>dcp-issuer</artifactId>
    <name>DCP Issuer Module</name>
    <description>Standalone Verifiable Credentials Issuer Service</description>
    
    <dependencies>
        <!-- Internal dependencies -->
        <dependency>
            <groupId>it.eng</groupId>
            <artifactId>dcp-common</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>it.eng</groupId>
            <artifactId>tools</artifactId>
            <version>${revision}</version>
        </dependency>
        
        <!-- Spring Boot starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>2.17.1</version>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Nimbus JOSE + JWT -->
        <dependency>
            <groupId>com.nimbusds</groupId>
            <artifactId>nimbus-jose-jwt</artifactId>
            <version>10.5</version>
        </dependency>
        
        <!-- Test dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <finalName>dcp-issuer</finalName>
        <plugins>
            <!-- Spring Boot Maven Plugin for executable JAR -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>3.1.2</version>
                <configuration>
                    <mainClass>it.eng.dcp.issuer.IssuerApplication</mainClass>
                    <layout>JAR</layout>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            
            <!-- Copy dependencies for Docker -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <executions>
                    <execution>
                        <id>copy-dependencies</id>
                        <phase>prepare-package</phase>
                        <goals>
                            <goal>copy-dependencies</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>${project.build.directory}/dependency-jars</outputDirectory>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## Application Class

### `IssuerApplication.java`

```java
package it.eng.dcp.issuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for the DCP Issuer service.
 * 
 * This application provides Verifiable Credentials issuance capabilities
 * including credential request processing, approval workflows, and 
 * automated key rotation.
 */
@SpringBootApplication
@EnableScheduling
@EnableMongoRepositories(basePackages = "it.eng.dcp.issuer.repository")
public class IssuerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(IssuerApplication.class, args);
    }
}
```

## Configuration Classes

### `IssuerProperties.java`

```java
package it.eng.dcp.issuer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the DCP Issuer module.
 * Binds properties under the `issuer` prefix.
 */
@ConfigurationProperties(prefix = "issuer")
@Validated
@Data
public class IssuerProperties {
    
    /**
     * The issuer's DID (e.g., did:web:localhost%3A8084:issuer).
     */
    @NotNull
    private String did;
    
    /**
     * Base URL for the issuer service.
     */
    @NotNull
    private String baseUrl;
    
    /**
     * Allowed clock skew in seconds for token validation.
     */
    @Min(0)
    private int clockSkewSeconds = 120;
    
    /**
     * Supported VC profiles (e.g., VC11_SL2021_JWT).
     */
    private List<String> supportedProfiles = new ArrayList<>();
    
    /**
     * Keystore configuration.
     */
    private Keystore keystore = new Keystore();
    
    /**
     * MongoDB configuration.
     */
    private Mongodb mongodb = new Mongodb();
    
    @Data
    public static class Keystore {
        /**
         * Path to PKCS12 keystore file.
         */
        private String path = "classpath:eckey-issuer.p12";
        
        /**
         * Keystore password.
         */
        private String password = "password";
        
        /**
         * Default key alias.
         */
        private String alias = "issuer";
        
        /**
         * Number of days before automatic key rotation.
         */
        @Min(1)
        private int rotationDays = 90;
    }
    
    @Data
    public static class Mongodb {
        /**
         * MongoDB host.
         */
        private String host = "localhost";
        
        /**
         * MongoDB port.
         */
        private int port = 27017;
        
        /**
         * Database name.
         */
        private String database = "issuer_db";
        
        /**
         * Authentication database.
         */
        private String authenticationDatabase = "admin";
        
        /**
         * Username (optional).
         */
        private String username;
        
        /**
         * Password (optional).
         */
        private String password;
    }
}
```

### `IssuerAutoConfiguration.java`

```java
package it.eng.dcp.issuer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the DCP Issuer module.
 * 
 * This configuration is activated when the application starts.
 * It enables issuer properties binding and component scanning.
 */
@Configuration
@ConditionalOnProperty(prefix = "issuer", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IssuerProperties.class)
@Import({IssuerMongoConfig.class, CredentialMetadataConfig.class})
@ComponentScan("it.eng.dcp.issuer")
public class IssuerAutoConfiguration {
}
```

### `IssuerMongoConfig.java`

```java
package it.eng.dcp.issuer.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for the Issuer module.
 */
@Configuration
@EnableMongoRepositories(basePackages = "it.eng.dcp.issuer.repository")
public class IssuerMongoConfig extends AbstractMongoClientConfiguration {
    
    @Autowired
    private IssuerProperties issuerProperties;
    
    @Override
    protected String getDatabaseName() {
        return issuerProperties.getMongodb().getDatabase();
    }
    
    @Override
    @Bean
    public MongoClient mongoClient() {
        IssuerProperties.Mongodb mongo = issuerProperties.getMongodb();
        
        String connectionString;
        if (mongo.getUsername() != null && !mongo.getUsername().isEmpty()) {
            connectionString = String.format(
                "mongodb://%s:%s@%s:%d/%s?authSource=%s",
                mongo.getUsername(),
                mongo.getPassword(),
                mongo.getHost(),
                mongo.getPort(),
                mongo.getDatabase(),
                mongo.getAuthenticationDatabase()
            );
        } else {
            connectionString = String.format(
                "mongodb://%s:%d/%s",
                mongo.getHost(),
                mongo.getPort(),
                mongo.getDatabase()
            );
        }
        
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connectionString))
            .build();
        
        return MongoClients.create(settings);
    }
    
    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), getDatabaseName());
    }
}
```

## Controllers

### `IssuerDidDocumentController.java`

```java
package it.eng.dcp.issuer.rest;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.issuer.service.IssuerDidDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for serving the Issuer's DID Document.
 * 
 * The DID Document provides public key information and service endpoints
 * for the issuer, allowing other parties to verify credentials and 
 * discover the issuer's capabilities.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class IssuerDidDocumentController {
    
    private final IssuerDidDocumentService didDocumentService;
    
    @Autowired
    public IssuerDidDocumentController(IssuerDidDocumentService didDocumentService) {
        this.didDocumentService = didDocumentService;
    }
    
    /**
     * Get the Issuer's DID Document.
     * 
     * @return DID Document containing issuer's public keys and service endpoints
     */
    @GetMapping("/.well-known/did.json")
    public ResponseEntity<DidDocument> getIssuerDidDocument() {
        DidDocument didDocument = didDocumentService.provideIssuerDidDocument();
        return ResponseEntity.ok(didDocument);
    }
}
```

### `IssuerAdminController.java`

```java
package it.eng.dcp.issuer.rest;

import it.eng.dcp.issuer.service.IssuerKeyRotationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Administrative endpoints for issuer operations.
 * 
 * These endpoints should be protected with appropriate authentication
 * in production environments.
 */
@RestController
@RequestMapping(path = "/issuer/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class IssuerAdminController {
    
    private final IssuerKeyRotationService keyRotationService;
    
    @Autowired
    public IssuerAdminController(IssuerKeyRotationService keyRotationService) {
        this.keyRotationService = keyRotationService;
    }
    
    /**
     * Manually trigger key rotation.
     * 
     * This endpoint allows administrators to rotate the issuer's signing keys
     * on demand, outside of the automatic rotation schedule.
     * 
     * @return Success message with new key alias
     */
    @PostMapping("/rotate-key")
    public ResponseEntity<?> rotateKey() {
        try {
            log.info("Manual key rotation requested");
            String newAlias = keyRotationService.rotateKey();
            
            return ResponseEntity.ok(Map.of(
                "message", "Key rotated successfully",
                "newKeyAlias", newAlias,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Failed to rotate key: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Key rotation failed",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Get current active key information.
     * 
     * @return Active key metadata
     */
    @PostMapping("/active-key")
    public ResponseEntity<?> getActiveKey() {
        try {
            var keyMetadata = keyRotationService.getActiveKeyMetadata();
            
            if (keyMetadata.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "No active key found"
                ));
            }
            
            var meta = keyMetadata.get();
            return ResponseEntity.ok(Map.of(
                "alias", meta.getAlias(),
                "createdAt", meta.getCreatedAt().toString(),
                "active", meta.isActive()
            ));
        } catch (Exception e) {
            log.error("Failed to get active key: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve active key",
                "message", e.getMessage()
            ));
        }
    }
}
```

## Services

### `IssuerDidDocumentService.java`

```java
package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.issuer.config.IssuerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for generating the Issuer's DID Document.
 * 
 * The DID Document contains the issuer's public verification keys
 * and service endpoints, enabling other parties to:
 * - Verify credentials signed by this issuer
 * - Discover the issuer's service endpoints
 * - Validate the issuer's identity
 */
@Service
@Slf4j
public class IssuerDidDocumentService {
    
    private final IssuerKeyService keyService;
    private final IssuerKeyMetadataService keyMetadataService;
    private final IssuerProperties issuerProperties;
    private final boolean sslEnabled;
    private final String serverPort;
    
    @Autowired
    public IssuerDidDocumentService(
            IssuerKeyService keyService,
            IssuerKeyMetadataService keyMetadataService,
            IssuerProperties issuerProperties,
            @Value("${server.ssl.enabled:false}") boolean sslEnabled,
            @Value("${server.port}") String serverPort) {
        this.keyService = keyService;
        this.keyMetadataService = keyMetadataService;
        this.issuerProperties = issuerProperties;
        this.sslEnabled = sslEnabled;
        this.serverPort = serverPort;
    }
    
    /**
     * Generate the Issuer's DID Document.
     * 
     * @return DidDocument containing issuer's keys and service endpoints
     */
    public DidDocument provideIssuerDidDocument() {
        // Get active key alias from metadata
        String activeAlias = keyMetadataService.getActiveKeyMetadata()
            .map(m -> m.getAlias())
            .orElse(issuerProperties.getKeystore().getAlias());
        
        // Load key pair for active alias
        keyService.loadKeyPairFromP12(
            issuerProperties.getKeystore().getPath(),
            issuerProperties.getKeystore().getPassword(),
            activeAlias
        );
        
        String issuerDid = issuerProperties.getDid();
        String protocol = sslEnabled ? "https" : "http";
        String baseEndpoint = issuerProperties.getBaseUrl() != null 
            ? issuerProperties.getBaseUrl()
            : protocol + "://localhost:" + serverPort;
        
        log.info("Generating DID document for issuer: {}", issuerDid);
        
        return DidDocument.Builder.newInstance()
            .id(issuerDid)
            .service(List.of(
                new ServiceEntry(
                    "IssuerService",
                    "IssuerService",
                    baseEndpoint + "/issuer"
                )
            ))
            .verificationMethod(List.of(
                VerificationMethod.Builder.newInstance()
                    .id(issuerDid + "#" + keyService.getKidFromPublicKey())
                    .type("JsonWebKey2020")
                    .controller(issuerDid)
                    .publicKeyJwk(keyService.convertPublicKeyToJWK())
                    .build()
            ))
            .build();
    }
}
```

### `IssuerKeyRotationService.java`

```java
package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.issuer.config.IssuerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service for automated and manual key rotation.
 * 
 * This service:
 * - Automatically rotates keys based on configured rotation period
 * - Provides manual rotation capability
 * - Maintains key history for credential verification
 * - Archives old keys for audit purposes
 */
@Service
@Slf4j
public class IssuerKeyRotationService {
    
    private final IssuerKeyService keyService;
    private final IssuerKeyMetadataService keyMetadataService;
    private final IssuerProperties issuerProperties;
    
    @Autowired
    public IssuerKeyRotationService(
            IssuerKeyService keyService,
            IssuerKeyMetadataService keyMetadataService,
            IssuerProperties issuerProperties) {
        this.keyService = keyService;
        this.keyMetadataService = keyMetadataService;
        this.issuerProperties = issuerProperties;
    }
    
    /**
     * Scheduled task to check and rotate keys automatically.
     * Runs daily at 2:00 AM server time.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndRotateKeys() {
        log.info("Starting scheduled key rotation check");
        
        Optional<KeyMetadata> activeKey = keyMetadataService.getActiveKeyMetadata();
        
        if (activeKey.isEmpty()) {
            log.warn("No active key found, generating initial key");
            rotateKey();
            return;
        }
        
        Instant createdAt = activeKey.get().getCreatedAt();
        long daysSinceCreation = ChronoUnit.DAYS.between(createdAt, Instant.now());
        int rotationDays = issuerProperties.getKeystore().getRotationDays();
        
        if (daysSinceCreation >= rotationDays) {
            log.info("Key rotation required: key age {} days exceeds threshold of {} days", 
                daysSinceCreation, rotationDays);
            rotateKey();
        } else {
            log.info("Key rotation not needed: key age {} days is within threshold of {} days",
                daysSinceCreation, rotationDays);
        }
    }
    
    /**
     * Manually rotate the issuer's signing key.
     * 
     * This method:
     * 1. Generates a new EC key pair
     * 2. Persists it to the keystore
     * 3. Updates key metadata in the database
     * 4. Archives the previous key
     * 
     * @return The new key alias
     */
    @Transactional
    public String rotateKey() {
        log.info("Starting key rotation");
        
        String keystorePath = resolveKeystorePath();
        String password = issuerProperties.getKeystore().getPassword();
        
        try {
            String newAlias = keyService.rotateKeyAndUpdateMetadata(keystorePath, password);
            log.info("Key rotation completed successfully, new alias: {}", newAlias);
            return newAlias;
        } catch (Exception e) {
            log.error("Key rotation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to rotate key", e);
        }
    }
    
    /**
     * Get active key metadata.
     * 
     * @return Optional containing active key metadata if present
     */
    public Optional<KeyMetadata> getActiveKeyMetadata() {
        return keyMetadataService.getActiveKeyMetadata();
    }
    
    /**
     * Resolve keystore path, handling classpath: prefix.
     * 
     * @return Resolved file system path
     */
    private String resolveKeystorePath() {
        String path = issuerProperties.getKeystore().getPath();
        
        // If classpath resource, extract to temp file for writing
        if (path.startsWith("classpath:")) {
            // For production, keystore should be externalized
            // This is a simplified implementation
            log.warn("Keystore path uses classpath: prefix. For production, use file system path.");
            return path.substring("classpath:".length());
        }
        
        return path;
    }
}
```

## Application Properties

### `application.properties`

```properties
# ============================================================================
# DCP Issuer Service - Main Configuration
# ============================================================================

spring.application.name=dcp-issuer
server.port=8084

# ============================================================================
# Issuer Identity Configuration
# ============================================================================
issuer.did=did:web:localhost%3A8084:issuer
issuer.base-url=http://localhost:8084
issuer.clock-skew-seconds=120

# Supported VC profiles
issuer.supported-profiles[0]=VC11_SL2021_JWT
issuer.supported-profiles[1]=VC11_SL2021_JSONLD

# ============================================================================
# Keystore Configuration
# ============================================================================
issuer.keystore.path=classpath:eckey-issuer.p12
issuer.keystore.password=password
issuer.keystore.alias=issuer
issuer.keystore.rotation-days=90

# ============================================================================
# MongoDB Configuration
# ============================================================================
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=issuer_db
spring.data.mongodb.authentication-database=admin
# Optional authentication
#spring.data.mongodb.username=issuer
#spring.data.mongodb.password=issuerpass

issuer.mongodb.host=${spring.data.mongodb.host}
issuer.mongodb.port=${spring.data.mongodb.port}
issuer.mongodb.database=${spring.data.mongodb.database}
issuer.mongodb.authentication-database=${spring.data.mongodb.authentication-database}

# ============================================================================
# SSL Configuration
# ============================================================================
server.ssl.enabled=false
#server.ssl.key-store=classpath:certs/issuer.p12
#server.ssl.key-store-password=password
#server.ssl.key-store-type=PKCS12
#server.ssl.key-alias=issuer

# ============================================================================
# Actuator Endpoints
# ============================================================================
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized

# ============================================================================
# Logging Configuration
# ============================================================================
logging.level.it.eng.dcp.issuer=INFO
logging.level.org.springframework.data.mongodb=WARN
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg%n
logging.file.name=logs/issuer.log

# ============================================================================
# Credential Metadata Configuration
# See credential-metadata-configuration.properties for detailed VC config
# ============================================================================
```

### `credential-metadata-configuration.properties`

```properties
# ============================================================================
# Credential Metadata Configuration for Issuer
# ============================================================================

# Credential 1: Membership Credential
issuer.credentials.supported[0].id=MembershipCredential
issuer.credentials.supported[0].type=CredentialObject
issuer.credentials.supported[0].credential-type=MembershipCredential
issuer.credentials.supported[0].profile=VC11_SL2021_JWT
issuer.credentials.supported[0].format=jwt_vc
issuer.credentials.supported[0].description=Dataspace membership credential

# Credential 2: Data Processor Credential
issuer.credentials.supported[1].id=DataProcessorCredential
issuer.credentials.supported[1].type=CredentialObject
issuer.credentials.supported[1].credential-type=DataProcessorCredential
issuer.credentials.supported[1].profile=VC11_SL2021_JWT
issuer.credentials.supported[1].format=jwt_vc
issuer.credentials.supported[1].description=Data processor role credential

# Add more credentials as needed...
```

## Docker Configuration

### `Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jre-alpine

# Create application directories
RUN mkdir -p /home/nobody/app && \
    mkdir -p /home/nobody/config && \
    mkdir -p /home/nobody/data/log/issuer && \
    mkdir -p /home/nobody/keystore

WORKDIR /home/nobody

# Copy application JAR
ADD /target/dcp-issuer.jar /home/nobody/app/dcp-issuer.jar

# Copy default configuration (can be overridden by volumes)
COPY /target/classes/application.properties /home/nobody/app/application.properties
COPY /target/classes/eckey-issuer.p12 /home/nobody/keystore/eckey-issuer.p12

# Set permissions
RUN chown -R nobody:nogroup /home/nobody

USER 65534

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "/home/nobody/app/dcp-issuer.jar"]
```

### `docker-compose.yml`

```yaml
version: '3.8'

services:
  issuer-mongodb:
    container_name: issuer-mongodb
    image: mongo:7.0.12
    ports:
      - "27018:27017"
    volumes:
      - issuer-mongodb-data:/data/db
      - issuer-mongodb-configdb:/data/configdb
    environment:
      - MONGO_INITDB_DATABASE=issuer_db
    networks:
      - issuer-network
    restart: unless-stopped

  dcp-issuer:
    container_name: dcp-issuer
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8084:8084"
    depends_on:
      - issuer-mongodb
    environment:
      # Spring profiles
      - SPRING_PROFILES_ACTIVE=production
      
      # MongoDB connection
      - SPRING_DATA_MONGODB_HOST=issuer-mongodb
      - SPRING_DATA_MONGODB_PORT=27017
      - SPRING_DATA_MONGODB_DATABASE=issuer_db
      
      # Issuer identity
      - ISSUER_DID=did:web:issuer.example.com
      - ISSUER_BASE_URL=https://issuer.example.com
      
      # Keystore
      - ISSUER_KEYSTORE_PATH=/home/nobody/keystore/eckey-issuer.p12
      - ISSUER_KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD:-password}
      - ISSUER_KEYSTORE_ROTATION_DAYS=90
      
      # JVM options
      - JAVA_OPTS=-Xms512m -Xmx1024m
    volumes:
      - ./config:/home/nobody/config:ro
      - ./keystore:/home/nobody/keystore
      - issuer-logs:/home/nobody/data/log/issuer
    networks:
      - issuer-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

volumes:
  issuer-mongodb-data:
    driver: local
  issuer-mongodb-configdb:
    driver: local
  issuer-logs:
    driver: local

networks:
  issuer-network:
    driver: bridge
```

### `docker-compose-dev.yml` (Development override)

```yaml
version: '3.8'

services:
  issuer-mongodb:
    ports:
      - "27018:27017"
    environment:
      - MONGO_INITDB_DATABASE=issuer_db_dev

  dcp-issuer:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8084:8084"
      - "5005:5005"  # Debug port
    environment:
      - SPRING_PROFILES_ACTIVE=development
      - ISSUER_DID=did:web:localhost%3A8084:issuer
      - ISSUER_BASE_URL=http://localhost:8084
      - JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Xms256m -Xmx512m
    volumes:
      - ./target/dcp-issuer.jar:/home/nobody/app/dcp-issuer.jar
      - ./config:/home/nobody/config
      - ./logs:/home/nobody/data/log/issuer
```

## Build & Run Scripts

### `build.sh` (Linux/Mac)

```bash
#!/bin/bash

echo "Building DCP Issuer Module..."

# Build the project
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "JAR location: target/dcp-issuer.jar"
else
    echo "Build failed!"
    exit 1
fi
```

### `build.cmd` (Windows)

```cmd
@echo off

echo Building DCP Issuer Module...

mvn clean package -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo Build successful!
    echo JAR location: target\dcp-issuer.jar
) else (
    echo Build failed!
    exit /b 1
)
```

### `run-docker.sh` (Linux/Mac)

```bash
#!/bin/bash

echo "Building and starting DCP Issuer with Docker Compose..."

# Build the JAR
./build.sh

if [ $? -ne 0 ]; then
    exit 1
fi

# Build and start Docker containers
docker-compose up --build -d

echo "Issuer service starting..."
echo "DID Document: http://localhost:8084/.well-known/did.json"
echo "Health check: http://localhost:8084/actuator/health"
echo ""
echo "View logs with: docker-compose logs -f dcp-issuer"
```

---

**Document Version**: 1.0  
**Created**: December 18, 2025  
**Status**: Technical Specification

