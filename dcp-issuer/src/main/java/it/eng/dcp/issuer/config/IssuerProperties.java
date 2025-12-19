package it.eng.dcp.issuer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Configuration properties for the DCP Issuer module.
 * Binds properties under the `issuer` prefix.
 */
@Component
@ConfigurationProperties(prefix = "issuer")
public class IssuerProperties {

    /** The issuer DID (e.g. did:web:localhost%3A8084:issuer). */
    @NotNull
    private String did;

    /** Base URL used by the issuer when constructing endpoints. */
    @NotNull
    private String baseUrl;

    /** Allowed clock skew in seconds for token validation. Defaults to 120 seconds. */
    @Min(0)
    private int clockSkewSeconds = 120;

    /** Supported profiles, e.g. ["VC11_SL2021_JWT","VC11_SL2021_JSONLD"] */
    private List<String> supportedProfiles = List.of("VC11_SL2021_JWT");

    /** Keystore configuration for issuer signing keys. */
    private Keystore keystore = new Keystore();

    /** MongoDB configuration. */
    private Mongodb mongodb = new Mongodb();

    /** Supported credentials configuration. */
    private Credentials credentials = new Credentials();

    /**
     * Get the issuer DID.
     * @return issuer DID
     */
    public String getDid() {
        return did;
    }

    /**
     * Set the issuer DID.
     * @param did issuer DID
     */
    public void setDid(String did) {
        this.did = did;
    }

    /**
     * Get the base URL.
     * @return base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Set the base URL.
     * @param baseUrl base URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Get the clock skew in seconds.
     * @return clock skew seconds
     */
    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    /**
     * Set the clock skew in seconds.
     * @param clockSkewSeconds clock skew seconds
     */
    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /**
     * Get the list of supported profiles.
     * @return supported profiles
     */
    public List<String> getSupportedProfiles() {
        return supportedProfiles;
    }

    /**
     * Set the list of supported profiles.
     * @param supportedProfiles supported profiles
     */
    public void setSupportedProfiles(List<String> supportedProfiles) {
        this.supportedProfiles = supportedProfiles;
    }

    /**
     * Get the keystore configuration.
     * @return keystore configuration
     */
    public Keystore getKeystore() {
        return keystore;
    }

    /**
     * Set the keystore configuration.
     * @param keystore keystore configuration
     */
    public void setKeystore(Keystore keystore) {
        this.keystore = keystore;
    }

    /**
     * Get the MongoDB configuration.
     * @return MongoDB configuration
     */
    public Mongodb getMongodb() {
        return mongodb;
    }

    /**
     * Set the MongoDB configuration.
     * @param mongodb MongoDB configuration
     */
    public void setMongodb(Mongodb mongodb) {
        this.mongodb = mongodb;
    }

    /**
     * Get the credentials configuration.
     * @return credentials configuration
     */
    public Credentials getCredentials() {
        return credentials;
    }

    /**
     * Set the credentials configuration.
     * @param credentials credentials configuration
     */
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    /**
     * Keystore configuration properties.
     */
    public static class Keystore {
        /** Path to the keystore file (e.g., classpath:eckey-issuer.p12). */
        private String path = "classpath:eckey-issuer.p12";

        /** Keystore password. */
        private String password;

        /** Key alias in the keystore. */
        private String alias = "issuer";

        /** Number of days before key rotation is required. */
        @Min(1)
        private int rotationDays = 90;

        /**
         * Get the keystore path.
         * @return keystore path
         */
        public String getPath() {
            return path;
        }

        /**
         * Set the keystore path.
         * @param path keystore path
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Get the keystore password.
         * @return keystore password
         */
        public String getPassword() {
            return password;
        }

        /**
         * Set the keystore password.
         * @param password keystore password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Get the key alias.
         * @return key alias
         */
        public String getAlias() {
            return alias;
        }

        /**
         * Set the key alias.
         * @param alias key alias
         */
        public void setAlias(String alias) {
            this.alias = alias;
        }

        /**
         * Get the rotation days.
         * @return rotation days
         */
        public int getRotationDays() {
            return rotationDays;
        }

        /**
         * Set the rotation days.
         * @param rotationDays rotation days
         */
        public void setRotationDays(int rotationDays) {
            this.rotationDays = rotationDays;
        }
    }

    /**
     * MongoDB configuration properties.
     */
    public static class Mongodb {
        /** MongoDB host. */
        private String host = "localhost";

        /** MongoDB port. */
        private int port = 27017;

        /** Database name. */
        private String database = "issuer_db";

        /** MongoDB username (optional). */
        private String username;

        /** MongoDB password (optional). */
        private String password;

        /** Authentication database. */
        private String authenticationDatabase = "admin";

        /**
         * Get the MongoDB host.
         * @return MongoDB host
         */
        public String getHost() {
            return host;
        }

        /**
         * Set the MongoDB host.
         * @param host MongoDB host
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * Get the MongoDB port.
         * @return MongoDB port
         */
        public int getPort() {
            return port;
        }

        /**
         * Set the MongoDB port.
         * @param port MongoDB port
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * Get the database name.
         * @return database name
         */
        public String getDatabase() {
            return database;
        }

        /**
         * Set the database name.
         * @param database database name
         */
        public void setDatabase(String database) {
            this.database = database;
        }

        /**
         * Get the MongoDB username.
         * @return MongoDB username
         */
        public String getUsername() {
            return username;
        }

        /**
         * Set the MongoDB username.
         * @param username MongoDB username
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Get the MongoDB password.
         * @return MongoDB password
         */
        public String getPassword() {
            return password;
        }

        /**
         * Set the MongoDB password.
         * @param password MongoDB password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Get the authentication database.
         * @return authentication database
         */
        public String getAuthenticationDatabase() {
            return authenticationDatabase;
        }

        /**
         * Set the authentication database.
         * @param authenticationDatabase authentication database
         */
        public void setAuthenticationDatabase(String authenticationDatabase) {
            this.authenticationDatabase = authenticationDatabase;
        }
    }

    /**
     * Credentials configuration properties.
     */
    public static class Credentials {
        /** List of supported credentials. */
        private List<SupportedCredential> supported = Collections.emptyList();

        /**
         * Get the list of supported credentials.
         * @return list of supported credentials
         */
        public List<SupportedCredential> getSupported() {
            return supported;
        }

        /**
         * Set the list of supported credentials.
         * @param supported list of supported credentials
         */
        public void setSupported(List<SupportedCredential> supported) {
            this.supported = supported;
        }
    }

    /**
     * Supported credential configuration.
     */
    public static class SupportedCredential {
        private String id;
        private String credentialType;
        private String profile;
        private String format = "jwt_vc";

        /**
         * Get the credential ID.
         * @return credential ID
         */
        public String getId() {
            return id;
        }

        /**
         * Set the credential ID.
         * @param id credential ID
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Get the credential type.
         * @return credential type
         */
        public String getCredentialType() {
            return credentialType;
        }

        /**
         * Set the credential type.
         * @param credentialType credential type
         */
        public void setCredentialType(String credentialType) {
            this.credentialType = credentialType;
        }

        /**
         * Get the profile.
         * @return profile
         */
        public String getProfile() {
            return profile;
        }

        /**
         * Set the profile.
         * @param profile profile
         */
        public void setProfile(String profile) {
            this.profile = profile;
        }

        /**
         * Get the format.
         * @return format
         */
        public String getFormat() {
            return format;
        }

        /**
         * Set the format.
         * @param format format
         */
        public void setFormat(String format) {
            this.format = format;
        }
    }
}
