package it.eng.negotiation.model;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import it.eng.tools.model.IConstants;

public class NegotiationMockObjectUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
	public static final String CALLBACK_ADDRESS = "https://callback.address/callback";
	public static final String FORWARD_TO = "https://forward-to.com";
	// must match dataset_id from initial_data.json 
	public static final String DATASET_ID = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
	public static final String ASSIGNEE = "urn:uuid:ASSIGNEE_CONSUMER";
	public static final String ASSIGNER = "urn:uuid:ASSIGNER_PROVIDER";
	
	// target for offer must be dataset_id
	public static final String TARGET = DATASET_ID;
	
	public static String generateUUID() {
		return "urn:uuid:" + UUID.randomUUID().toString();
	}
	
	
	public static ContractNegotiationEventMessage getEventMessage(ContractNegotiationEventType eventType) {
		return ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
				.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
				.eventType(eventType)
				.build();
	}
	
	public static final ContractAgreementVerificationMessage CONTRACT_AGREEMENT_VERIFICATION_MESSAGE = ContractAgreementVerificationMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.build();

	public static final ContractNegotiationTerminationMessage TERMINATION_MESSAGE = ContractNegotiationTerminationMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.code("Test")
			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("test").build()))
			.build();
	
	public static final Constraint CONSTRAINT = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.GT)
			.rightOperand("2024-02-29T00:00:01+01:00")
			.build();
	
	public static final Constraint CONSTRAINT_COUNT_5 = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.COUNT)
			.operator(Operator.LTEQ)
			.rightOperand("5")
			.build();
	
	public static final Permission PERMISSION = Permission.Builder.newInstance()
			.action(Action.USE)
			.target(NegotiationMockObjectUtil.TARGET)
			.constraint(Arrays.asList(NegotiationMockObjectUtil.CONSTRAINT))
			.build();
	
	public static final Permission PERMISSION_COUNT_5 = Permission.Builder.newInstance()
			.action(Action.USE)
			.target(NegotiationMockObjectUtil.TARGET)
			.constraint(Arrays.asList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
			.build();
	
	public static final Offer OFFER = Offer.Builder.newInstance()
			.target(NegotiationMockObjectUtil.TARGET)
			.assignee(NegotiationMockObjectUtil.ASSIGNEE)
			.assigner(NegotiationMockObjectUtil.ASSIGNER)
			.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION))
			.build();
	
	public static final Offer OFFER_WITH_ORIGINAL_ID = Offer.Builder.newInstance()
			.target(NegotiationMockObjectUtil.TARGET)
			.assignee(NegotiationMockObjectUtil.ASSIGNEE)
			.assigner(NegotiationMockObjectUtil.ASSIGNER)
			.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION))
			.originalId("some-original-id")
			.build();
	
	public static final Offer OFFER_COUNT_5 = Offer.Builder.newInstance()
			.target(NegotiationMockObjectUtil.TARGET)
			.assignee(NegotiationMockObjectUtil.ASSIGNEE)
			.assigner(NegotiationMockObjectUtil.ASSIGNER)
			.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_COUNT_5))
			.build();

	public static final ContractOfferMessage CONTRACT_OFFER_MESSAGE = ContractOfferMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
			.offer(NegotiationMockObjectUtil.OFFER)
			.build();

	public static final Agreement AGREEMENT = Agreement.Builder.newInstance()
			.id(NegotiationMockObjectUtil.generateUUID())
			.assignee(NegotiationMockObjectUtil.ASSIGNEE)
			.assigner(NegotiationMockObjectUtil.ASSIGNER)
			.target(NegotiationMockObjectUtil.TARGET)
			.timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
			.permission(Arrays.asList(PERMISSION_COUNT_5))
			.build();
	
	public static final ContractRequestMessage CONTRACT_REQUEST_MESSAGE = ContractRequestMessage.Builder.newInstance()
			.callbackAddress(CALLBACK_ADDRESS)
			.consumerPid(CONSUMER_PID)
			.offer(OFFER)
			.build();
	
	public static final ContractAgreementMessage CONTRACT_AGREEMENT_MESSAGE = ContractAgreementMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
			.agreement(AGREEMENT)
			.build();
	
	public static final ContractNegotiationEventMessage CONTRACT_NEGOTIATION_EVENT_MESSAGE = ContractNegotiationEventMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.eventType(ContractNegotiationEventType.FINALIZED)
			.build();
	
	public static final ContractNegotiationEventMessage CONTRACT_NEGOTIATION_EVENT_MESSAGE_ACCEPTED = ContractNegotiationEventMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.eventType(ContractNegotiationEventType.ACCEPTED)
			.build();
	
	public static final ContractNegotiationErrorMessage CONTRACT_NEGOTIATION_ERROR_MESSAGE = ContractNegotiationErrorMessage.Builder.newInstance()
			.consumerPid(CONSUMER_PID)
			.providerPid(PROVIDER_PID)
			.code(HttpStatus.NOT_FOUND.getReasonPhrase())
            .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value("Some reason").build()))
            .description(Collections.singletonList(Description.Builder.newInstance().language("en").value("Some description").build()))
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_ACCEPTED = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.ACCEPTED)
			.offer(NegotiationMockObjectUtil.OFFER_COUNT_5)
			.assigner(ASSIGNER)
			.role(IConstants.ROLE_CONSUMER)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_ACCEPTED_NO_OFFER = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.ACCEPTED)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_REQUESTED = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.REQUESTED)
			.offer(OFFER)
			.assigner(ASSIGNER)
			.role(IConstants.ROLE_CONSUMER)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_REQUESTED_PROIVDER = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.REQUESTED)
			.offer(OFFER)
			.assigner(ASSIGNER)
			.role(IConstants.ROLE_PROVIDER)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_AGREED = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.AGREED)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_OFFERED = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.OFFERED)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_VERIFIED = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.VERIFIED)
			.agreement(AGREEMENT)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_FINALIZED = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.FINALIZED)
			.build();
	
	public static final ContractNegotiation CONTRACT_NEGOTIATION_TERMINATED = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(CALLBACK_ADDRESS)
			.state(ContractNegotiationState.TERMINATED)
			.offer(NegotiationMockObjectUtil.OFFER_COUNT_5)
			.assigner(ASSIGNER)
			.build();
}
