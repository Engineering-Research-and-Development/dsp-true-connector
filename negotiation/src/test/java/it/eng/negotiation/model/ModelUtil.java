package it.eng.negotiation.model;

import java.util.Arrays;
import java.util.UUID;

public class ModelUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
	public static final String CALLBACK_ADDRESS = "https://callback.address/callback";
	public static final String DATASET_ID = "urn:uuid:DATASET_ID";
	public static final String ASSIGNEE = "urn:uuid:ASSIGNEE";
	public static final String ASSIGNER = "urn:uuid:ASSIGNER";
	
	public static final String TARGET = "urn:uuid:TARGET";
	
	public static String generateUUID() {
		return "urn:uuid:" + UUID.randomUUID().toString();
	}
	
	
	public static Constraint CONSTRAINT = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.GT)
			.rightOperand("2024-02-29T00:00:01+01:00")
			.build();
	
	public static Permission PERMISSION = Permission.Builder.newInstance()
			.action(Action.USE)
			.target(ModelUtil.TARGET)
			.constraint(Arrays.asList(ModelUtil.CONSTRAINT))
			.build();
	
	public static Offer OFFER = Offer.Builder.newInstance()
			.target(ModelUtil.TARGET)
			.assignee(ModelUtil.ASSIGNEE)
			.assigner(ModelUtil.ASSIGNER)
			.permission(Arrays.asList(ModelUtil.PERMISSION))
			.build();

	public static ContractOfferMessage CONTRACT_OFFER_MESSAGE = ContractOfferMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
			.offer(ModelUtil.OFFER)
			.build();

	public static Agreement AGREEMENT = Agreement.Builder.newInstance()
			.id(ModelUtil.generateUUID())
			.assignee(ModelUtil.ASSIGNEE)
			.assigner(ModelUtil.ASSIGNER)
			.target(ModelUtil.TARGET)
			.permission(Arrays.asList(Permission.Builder.newInstance()
					.action(Action.USE)
					.constraint(Arrays.asList(Constraint.Builder.newInstance()
							.leftOperand(LeftOperand.COUNT)
							.operator(Operator.EQ)
							.rightOperand("5")
							.build()))
					.build()))
			.build();
}
