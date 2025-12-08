package it.eng.tools.model;

public interface DSpaceConstants {

    public static enum ContractNegotiationStates {
        REQUESTED, OFFERED, ACCEPTED, AGREED, VERIFIED, FINALIZED, TERMINATED
    }

    public static enum DataTransferStates {
        INITIALIZED, REQUESTED, STARTED, COMPLETED, SUSPENDED, TERMINATED
    }

    public static enum ContractNegotiationEvent {
        ACCEPTED, FINALIZED;
    }

    public static enum Operators {
        EQ, GT, GTEQ, HAS_PARENT, IS_A, IS_ALL_OF, IS_ANY_OF, IS_NONE_OF, IS_PART_OF, LT, LTEQ, NEQ;
    }

    public static final String AGREEMENT = "agreement";
    public static final String AGREEMENT_ID = "agreementId";
    public static final String ACTION = "action";
    public static final String CALLBACK_ADDRESS = "callbackAddress";
    public static final String CONTEXT = "@context";
    public static final String ID = "@id";
    public static final String DSPACE_2025_01_CONTEXT = "https://w3id.org/dspace/2025/1/context.jsonld";
    public static final String DCP_CONTEXT = "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld";
    public static final String DID_CONTEXT = "https://www.w3.org/ns/did/v1";
    public static final String DCP_NAMESPACE = "https://w3id.org/dspace-dcp/v1.0";
    public static final String TYPE = "@type";
    public static final String VALUE = "value";
    public static final String LANGUAGE = "@language";
    public static final String CONSUMER_PID = "consumerPid";
    public static final String PROVIDER_PID = "providerPid";
    public static final String PARTICIPANT_ID = "participantId";
    public static final String DATA_ADDRESS = "dataAddress";


    public static final String ENDPOINT_TYPE = "endpointType";
    public static final String ENDPOINT = "endpoint";
    public static final String ENDPOINT_PROPERTIES = "endpointProperties";
    public static final String NAME = "name";
    public static final String FORMAT = "format";
    public static final String ENDPOINT_URL = "endpointURL";
    public static final String DATASET = "dataset";
    public static final String DISTRIBUTION = "distribution";
    public static final String KEYWORD = "keyword";
    public static final String THEME = "theme";
    public static final String CONFORMSTO = "conformsTo";
    public static final String CREATOR = "creator";
    public static final String DESCRIPTION = "description";
    public static final String IDENTIFIER = "identifier";
    public static final String ISSUED = "issued";
    public static final String MODIFIED = "modified";
    public static final String TITLE = "title";
    public static final String ACCESS_SERVICE = "accessService";
    public static final String SERVICE = "service";

    public static final String ENDPOINT_DESCRIPTION = "endpointDescription";

    public static final String PERMISSION = "permission";
    public static final String ASSIGNEE = "assignee";
    public static final String ASSIGNER = "assigner";
    public static final String OPERATOR = "operator";

    public static final String CODE = "code";
    public static final String CONSTRAINT = "constraint";
    public static final String EVENT_TYPE = "eventType";
    public static final String LEFT_OPERAND = "leftOperand";
    public static final String OFFER = "offer";
    public static final String REASON = "reason";
    public static final String RIGHT_OPERAND = "rightOperand";
    public static final String STATE = "state";
    public static final String TARGET = "target";
    public static final String TIMESTAMP = "timestamp";
    public static final String FILTER = "filter";
}
