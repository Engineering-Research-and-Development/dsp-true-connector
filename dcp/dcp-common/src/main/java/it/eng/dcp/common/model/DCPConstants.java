package it.eng.dcp.common.model;

public class DCPConstants {

    public static final String DCP_CONTEXT = "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld";
    public static final String DID_CONTEXT = "https://www.w3.org/ns/did/v1";
    public static final String DCP_NAMESPACE = "https://w3id.org/dspace-dcp/v1.0";

    public static final String ID = "@id";
    public static final String TYPE = "type";
    public static final String CONTEXT = "@context";

    // DCP Scope Aliases (per DCP spec Section 5.4.1.2)
    public static final String SCOPE_ALIAS_VC_TYPE = "org.eclipse.dspace.dcp.vc.type";
    public static final String SCOPE_ALIAS_VC_ID = "org.eclipse.dspace.dcp.vc.id";

    // DID Resolution Constants
    /** The did:web method prefix. */
    public static final String DID_WEB_PREFIX = "did:web:";

    /** The W3C standard well-known DID document path. */
    public static final String WELL_KNOWN_DID_PATH = "/.well-known/did.json";

    /** The legacy DID document filename. */
    public static final String LEGACY_DID_FILENAME = "/did.json";

    public static final String ISSUER_METADATA_PATH = "/metadata";
    public static final String ISSUER_CREDENTIALS_PATH = "/credentials";
}
