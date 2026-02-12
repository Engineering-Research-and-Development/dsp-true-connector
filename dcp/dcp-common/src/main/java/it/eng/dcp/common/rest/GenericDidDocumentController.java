package it.eng.dcp.common.rest;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.did.DidDocumentService;
import it.eng.dcp.common.util.DidPathResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Unified DID Document controller supporting multiple endpoint patterns.
 *
 * <p>Supports three endpoint patterns simultaneously:
 * <ol>
 *   <li><strong>W3C Standard (no path):</strong> {@code /.well-known/did.json}</li>
 *   <li><strong>W3C Standard (with path):</strong> {@code /{pathSegments}/.well-known/did.json}</li>
 *   <li><strong>Legacy convenience:</strong> {@code /{role}/did.json}</li>
 * </ol>
 *
 * <p>Endpoints are automatically registered based on DID configuration.
 * All endpoints serve the same DID document content.
 *
 * <p><strong>Examples:</strong>
 * <ul>
 *   <li>DID: {@code did:web:localhost:8080} → {@code /.well-known/did.json}</li>
 *   <li>DID: {@code did:web:localhost:8084:issuer} →
 *     <ul>
 *       <li>{@code /issuer/.well-known/did.json} (W3C with path)</li>
 *       <li>{@code /issuer/did.json} (legacy)</li>
 *       <li>{@code /.well-known/did.json} (fallback)</li>
 *     </ul>
 *   </li>
 *   <li>DID: {@code did:web:localhost:8080:api:v1:issuer} →
 *     <ul>
 *       <li>{@code /api/v1/issuer/.well-known/did.json} (W3C with deep path)</li>
 *       <li>{@code /issuer/did.json} (legacy, uses last segment)</li>
 *       <li>{@code /.well-known/did.json} (fallback)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><strong>Configuration:</strong>
 * <pre>
 * dcp.connector-did=did:web:localhost:8084:issuer
 * dcp.auto-register-path-endpoints=true   # Enable automatic path endpoint registration
 * dcp.enable-legacy-endpoints=true         # Enable legacy /role/did.json endpoints
 * </pre>
 *
 * <p>In single-module deployments (holder-only, issuer-only, verifier-only), the
 * module's configuration will be used. In multi-module test scenarios, the @Primary
 * configuration (typically holder) will be used.
 *
 * @see DidPathResolver
 * @see DcpProperties
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class GenericDidDocumentController implements InitializingBean {

    private final DidDocumentService didDocumentService;
    private final DcpProperties dcpProperties;
    private final RequestMappingHandlerMapping handlerMapping;
    private final DidDocumentConfig didDocumentConfig;

    /**
     * Constructor.
     *
     * @param didDocumentService service for DID document operations
     * @param dcpProperties DCP configuration properties
     * @param handlerMapping Spring handler mapping for dynamic endpoint registration
     * @param didDocumentConfig DID document configuration
     */
    @Autowired
    public GenericDidDocumentController(
            DidDocumentService didDocumentService,
            DcpProperties dcpProperties,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            DidDocumentConfig didDocumentConfig) {
        this.didDocumentService = didDocumentService;
        this.dcpProperties = dcpProperties;
        this.handlerMapping = handlerMapping;
        this.didDocumentConfig = didDocumentConfig;

        log.info("GenericDidDocumentController initialized");
        log.info("DID: {}", dcpProperties.getConnectorDid());
        log.info("Auto-register path endpoints: {}", dcpProperties.isAutoRegisterPathEndpoints());
        log.info("Enable legacy endpoints: {}", dcpProperties.isEnableLegacyEndpoints());
    }

    /**
     * Serves DID document at W3C standard well-known location (no path).
     * This endpoint is always available regardless of DID path segments.
     *
     * <p><strong>Endpoint:</strong> {@code GET /.well-known/did.json}
     *
     * @return DID document in JSON format
     */
    @GetMapping(value = "/.well-known/did.json")
    public ResponseEntity<DidDocument> getDidDocument() {
        log.debug("Serving DID document from well-known endpoint (no path)");
        DidDocument didDocument = didDocumentService.provideDidDocument(didDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }

    /**
     * Dynamic endpoint registration based on DID path segments.
     * Called during bean initialization (after all properties are set).
     *
     * <p>Registers additional endpoints if DID contains path segments:
     * <ul>
     *   <li>W3C-compliant path endpoint: {@code /{pathSegments}/.well-known/did.json}</li>
     *   <li>Legacy convenience endpoint: {@code /{role}/did.json} (if enabled)</li>
     * </ul>
     *
     * @throws Exception if endpoint registration fails
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // Ensure the handler mapping uses PathPatternParser (Spring Boot 3.x default)
        if (handlerMapping.getPatternParser() == null) {
            log.warn("HandlerMapping PatternParser is null, setting PathPatternParser explicitly");
            handlerMapping.setPatternParser(new PathPatternParser());
        }

        String connectorDid = dcpProperties.getConnectorDid();

        if (connectorDid == null || connectorDid.isBlank()) {
            log.warn("Connector DID is not configured. Skipping dynamic endpoint registration.");
            return;
        }

        // Extract path segments from configured DID
        List<String> pathSegments = DidPathResolver.extractPathSegments(connectorDid);

        if (pathSegments.isEmpty()) {
            log.info("DID has no path segments. Only /.well-known/did.json will be available.");
            return;
        }

        log.info("DID has {} path segment(s): {}", pathSegments.size(), pathSegments);

        if (!dcpProperties.isAutoRegisterPathEndpoints()) {
            log.info("Auto-registration of path endpoints is disabled. Skipping dynamic registration.");
            return;
        }

        // Register W3C-compliant path endpoint
        registerPathSpecificWellKnownEndpoint(pathSegments);

        // Register legacy convenience endpoint (if enabled)
        if (dcpProperties.isEnableLegacyEndpoints()) {
            registerLegacyConvenienceEndpoint(pathSegments);
        }

        log.info("Dynamic endpoint registration complete");
    }

    /**
     * Registers W3C-compliant path-specific well-known endpoint.
     *
     * <p>Examples:
     * <ul>
     *   <li>Path ["issuer"] → {@code /issuer/.well-known/did.json}</li>
     *   <li>Path ["api", "v1", "issuer"] → {@code /api/v1/issuer/.well-known/did.json}</li>
     * </ul>
     *
     * @param pathSegments list of path segments from DID
     */
    private void registerPathSpecificWellKnownEndpoint(List<String> pathSegments) {
        String endpointPath = DidPathResolver.buildWellKnownEndpointPath(pathSegments);

        try {
            // Get the handler method for serving DID documents
            Method handlerMethod = this.getClass().getMethod("serveDidDocument");

            // Get the builder configuration from handler mapping (now with PathPatternParser set)
            RequestMappingInfo.BuilderConfiguration config = handlerMapping.getBuilderConfiguration();

            // Create request mapping info using the handler mapping's configuration
            // This ensures we use PathPatternParser instead of AntPathMatcher
            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(endpointPath)
                    .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .options(config)
                    .build();

            // Register the mapping
            handlerMapping.registerMapping(mappingInfo, this, handlerMethod);

            log.info("✓ Registered W3C-compliant endpoint: GET {}", endpointPath);
        } catch (NoSuchMethodException e) {
            log.error("Failed to register path-specific well-known endpoint: {}", e.getMessage(), e);
        }
    }

    /**
     * Registers legacy convenience endpoint for backward compatibility.
     * Uses only the last path segment (typically the role name).
     *
     * <p>Examples:
     * <ul>
     *   <li>Path ["issuer"] → {@code /issuer/did.json}</li>
     *   <li>Path ["api", "v1", "issuer"] → {@code /issuer/did.json} (uses last segment)</li>
     * </ul>
     *
     * @param pathSegments list of path segments from DID
     */
    private void registerLegacyConvenienceEndpoint(List<String> pathSegments) {
        String legacyPath = DidPathResolver.buildLegacyEndpointPath(pathSegments);

        if (legacyPath == null) {
            log.debug("No legacy path to register");
            return;
        }

        try {
            // Get the handler method
            Method handlerMethod = this.getClass().getMethod("serveDidDocument");

            // Get the builder configuration from handler mapping (now with PathPatternParser set)
            RequestMappingInfo.BuilderConfiguration config = handlerMapping.getBuilderConfiguration();

            // Create request mapping info using the handler mapping's configuration
            // This ensures we use PathPatternParser instead of AntPathMatcher
            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(legacyPath)
                    .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .options(config)
                    .build();

            // Register the mapping
            handlerMapping.registerMapping(mappingInfo, this, handlerMethod);

            log.info("✓ Registered legacy endpoint: GET {}", legacyPath);
        } catch (NoSuchMethodException e) {
            log.error("Failed to register legacy convenience endpoint: {}", e.getMessage(), e);
        }
    }

    /**
     * Generic handler method for dynamically registered endpoints.
     * This method is used by both W3C-compliant and legacy endpoints.
     *
     * <p><strong>Note:</strong> This method is called via reflection for dynamically
     * registered endpoints. The static {@code /.well-known/did.json} endpoint uses
     * {@link #getDidDocument()} directly.
     *
     * @return DID document in JSON format
     */
    public ResponseEntity<DidDocument> serveDidDocument() {
        log.debug("Serving DID document from dynamically registered endpoint");
        DidDocument didDocument = didDocumentService.provideDidDocument(didDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }
}
