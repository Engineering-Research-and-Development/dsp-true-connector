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

	public static final String DSPACE = "dspace:";
	public static final String DCAT = "dcat:";
	public static final String DCT = "dct:";

	public static final String AGREEMENT = "agreement";
	public static final String AGREEMENT_ID ="agreementId";
	public static final String DSPACE_AGREEMENT = DSPACE + AGREEMENT;
	public static final String DSPACE_AGREEMENT_ID = DSPACE + AGREEMENT_ID;
	public static final String ACTION = "action";
	public static final String ALGORITHM = "algorithm";
	public static final String CALLBACK_ADDRESS = "callbackAddress";
	public static final String DSPACE_CALLBACK_ADDRESS = DSPACE + CALLBACK_ADDRESS;
	public static final String CONTEXT = "@context";
	public static final String ID = "@id";
	public static final String DATASPACE_CONTEXT_0_8_VALUE = "https://w3id.org/dspace/2024/1/context.json";
	public static final String DATASET_CONTEXT_VALUE =       "https://w3id.org/dspace/1/0/context.json";
	public static final String TYPE = "@type";
	public static final String VALUE = "@value";
	public static final String LANGUAGE = "@language";
	public static final String CONSUMER_PID = "consumerPid";
	public static final String PROVIDER_PID = "providerPid";
	public static final String DSPACE_CONSUMER_PID = DSPACE + CONSUMER_PID;
	public static final String DSPACE_PROVIDER_PID = DSPACE + PROVIDER_PID;
	public static final String PARTICIPANT_ID = "participantId";
	public static final String DSPACE_PARTICIPANT_ID = DSPACE + PARTICIPANT_ID;
	public static final String DATA_ADDRESS = "dataAddress";
	public static final String DSPACE_DATA_ADDRESS = DSPACE + DATA_ADDRESS;
	
	
	public static final String ENDPOINT_TYPE = "endpointType";
	public static final String DSPACE_ENDPOINT_TYPE = DSPACE + ENDPOINT_TYPE;
	public static final String ENDPOINT = "endpoint";
	public static final String DSPACE_ENDPOINT = DSPACE + ENDPOINT;
	public static final String ENDPOINT_PROPERTIES = "endpointProperties";
	public static final String DSPACE_ENDPOINT_PROPERTIES = DSPACE + ENDPOINT_PROPERTIES;
	public static final String NAME = "name";
	public static final String DSPACE_NAME = DSPACE + NAME;
	public static final String DSPACE_VALUE = DSPACE + "value";
	public static final String FORMAT = "format";
	public static final String DCT_FORMAT = DCT + FORMAT;
	public static final String DCT_TERMS = DCT + "terms";
	public static final String ENDPOINT_URL= "endpointURL";
	public static final String DCAT_ENDPOINT_URL = DCAT + ENDPOINT_URL;
	public static final String DATASET = "dataset";
	public static final String DSPACE_DATASET = DSPACE + DATASET;
	public static final String DCAT_DATASET = DCAT + DATASET;
	public static final String SERVES_DATASET = "servesDataset";
	public static final String DCAT_SERVES_DATASET = DCAT + SERVES_DATASET;
	public static final String DATA_SERVICE = "dataService";
	public static final String DCAT_DATA_SERVICE = DCAT + DATA_SERVICE;
	public static final String DISTRIBUTION = "distribution";
	public static final String DCAT_DISTRIBUTION = DCAT + DISTRIBUTION;
	public static final String POLICY_TYPE = "@policytype";
	public static final String CONTRACT_OFFER = DCAT + "contractOffer";
	public static final String KEYWORD = "keyword";
	public static final String DCAT_KEYWORD = DCAT + KEYWORD;
	public static final String THEME = "theme";
	public static final String DCAT_THEME = DCAT + THEME;
	public static final String CONFORMSTO = "conformsTo";
	public static final String DCT_CONFORMSTO = DCT + CONFORMSTO;
	public static final String CREATOR = "creator";
	public static final String DCT_CREATOR = DCAT + CREATOR;
	public static final String DESCRIPTION = "description";
	public static final String DCT_DESCRIPTION = DCT + DESCRIPTION;
	public static final String IDENTIFIER = "identifier";
	public static final String DCT_IDENTIFIER = DCAT + IDENTIFIER;
	public static final String ISSUED = "issued";
	public static final String DCT_ISSUED = DCAT + ISSUED;
	public static final String MODIFIED = "modified";
	public static final String DCT_MODIFIED = DCT + MODIFIED;
	public static final String TITLE = "title";
	public static final String DCT_TITLE = DCT + TITLE;
	public static final String ACCESS_SERVICE = "accessService";
	public static final String DCAT_ACCESS_SERVICE = DCAT + ACCESS_SERVICE;
	public static final String SERVICE = "service";
	public static final String DCAT_SERVICE = DCAT + SERVICE;
	
	public static final String ENDPOINT_DESCRIPTION = "endpointDescription";
	public static final String DCAT_ENDPOINT_DESCRIPTION = DCAT + ENDPOINT_DESCRIPTION;
	
	public static final String PERMISSION = "permission";
	public static final String ODRL = "odrl:";
	public static final String ODRL_SCHEMA = "http://www.w3.org/ns/odrl/2/";
	public static final String ODRL_ASSIGNEE = ODRL + "assignee";
	public static final String ODRL_ASSIGNER = ODRL + "assigner";
	public static final String ODRL_INHERITS_FROM = ODRL + "inheritFrom";
	public static final String ODRL_PROPERTY = ODRL + "property";
	public static final String ODRL_PERMISSION = ODRL + PERMISSION;
	public static final String ODRL_PROHIBITION = ODRL + "Prohibition";
	public static final String ODRL_TARGET = ODRL + "target";
	public static final String ODRL_ACTION = ODRL + ACTION;
	public static final String ODRL_CONSTRAINT = ODRL + "constraint";
	public static final String ODRL_DUTY = ODRL + "duty";
	public static final String ODRL_CONSEQUENCE = ODRL + "consequence";
	public static final String ODRL_ASSET = ODRL + "asset";
	public static final String ODRL_POLICY = ODRL + "policy";
	public static final String ODRL_INCLUDED_IN = ODRL + "includedIn";
	public static final String ODRL_OPERATOR = ODRL + "operator";
	public static final String ODRL_LEFT_OPERAND = ODRL + "leftOperand";
	public static final String ODRL_RIGHT_OPERAND = ODRL + "rightOperand";
	public static final String HAS_POLICY = "hasPolicy";
	public static final String ODRL_HAS_POLICY = ODRL + HAS_POLICY;
	public static final String CATALOG_CONTEXT_VALUE = "https://w3id.org/dspace/v0.8/context.json";
	public static final String CODE = "code";
	public static final String DSPACE_CODE = DSPACE + CODE;
	public static final String CREATED = "created";
	public static final String CONSTRAINT = "constraint";
	public static final String CRED = "cred:";
	public static final String CREDENTIAL_SUBJECT = "credentialSubject";
	public static final String DIGEST = "digest";
	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String EVENT_TYPE = "eventType";
	public static final String DSPACE_EVENT_TYPE = DSPACE + EVENT_TYPE;
	public static final String HASH = "hash";
	public static final String HASHED_MESSAGE = "hashedMessage";
	public static final String JWS = "jws";
	public static final String LEFT_OPERAND = "leftOperand";
	public static final String OFFER = "offer";
	public static final String DSPACE_OFFER = DSPACE + OFFER;
	public static final String ODRL_OFFER = ODRL + OFFER;
	public static final String OFFER_ID = "offerId";
	public static final String DSPACE_OFFER_ID = DSPACE + OFFER_ID;
	public static final String OPERAND = "operand";
	public static final String PROCESS_ID = "processId";
	public static final String PROOF = "proof";
	public static final String PROOF_PURPOSE = "proofPurpose";
	public static final String REASON = "reason";
	public static final String DSPACE_REASON = DSPACE + REASON;
	public static final String RIGHT_OPERAND = "rightOperand";
	public static final String SEC = "sec:";
	public static final String STATE = "state";
	public static final String DSPACE_STATE = DSPACE + STATE;
	public static final String TARGET = "target";
	public static final String TIMESTAMP = "timestamp";
	public static final String DSPACE_TIMESTAMP = DSPACE + TIMESTAMP;
	public static final String FILTER = "filter";
	public static final String DSPACE_FILTER = DSPACE + FILTER;
	public static final String PUBLISHER = DCT + "publisher";
	public static final String CATALOG_REQUEST_MESSAGE = DSPACE + "CatalogRequestMessage";

}
