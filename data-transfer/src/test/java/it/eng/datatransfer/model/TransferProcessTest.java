package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferProcessTest {

	private TransferProcess transferProcess = TransferProcess.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(TransferState.STARTED)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(transferProcess);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		
		TransferProcess javaObj = Serializer.deserializePlain(result, TransferProcess.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(transferProcess);
		assertNull(result.get(DSpaceConstants.ID));
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		
		TransferProcess javaObj = Serializer.deserializeProtocol(result, TransferProcess.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> TransferProcess.Builder.newInstance()
				.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(transferProcess);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, TransferProcess.class));
	}
	
	@Test
	@DisplayName("From initial TransferProcess with new TrasnferState")
	public void fromInitialWithNewState() {
		TransferProcess tpCopied = ModelUtil.TRANSFER_PROCESS_REQUESTED.copyWithNewTransferState(TransferState.STARTED);
		assertEquals(ModelUtil.TRANSFER_PROCESS_REQUESTED.getId(), tpCopied.getId());
		assertEquals(ModelUtil.CONSUMER_PID, tpCopied.getConsumerPid());
		assertEquals(ModelUtil.PROVIDER_PID, tpCopied.getProviderPid());
		assertEquals(ModelUtil.AGREEMENT_ID, tpCopied.getAgreementId());
		assertEquals(TransferState.STARTED, tpCopied.getState());
	}
	
	@Test
	@DisplayName("From Requested")
	public void fromRequested() {
		assertTrue(ModelUtil.TRANSFER_PROCESS_REQUESTED.getState().canTransitTo(TransferState.STARTED));
		assertTrue(ModelUtil.TRANSFER_PROCESS_REQUESTED.getState().canTransitTo(TransferState.TERMINATED));
		assertFalse(ModelUtil.TRANSFER_PROCESS_REQUESTED.getState().canTransitTo(TransferState.SUSPENDED));
		assertFalse(ModelUtil.TRANSFER_PROCESS_REQUESTED.getState().canTransitTo(TransferState.COMPLETED));
	}
	
	@Test
	@DisplayName("From Started")
	public void fromStarted() {
		assertTrue(ModelUtil.TRANSFER_PROCESS_STARTED.getState().canTransitTo(TransferState.SUSPENDED));
		assertTrue(ModelUtil.TRANSFER_PROCESS_STARTED.getState().canTransitTo(TransferState.TERMINATED));
		assertTrue(ModelUtil.TRANSFER_PROCESS_STARTED.getState().canTransitTo(TransferState.COMPLETED));
		assertFalse(ModelUtil.TRANSFER_PROCESS_STARTED.getState().canTransitTo(TransferState.REQUESTED));
	}
	
	@Test
	@DisplayName("From Suspended")
	public void fromSuspended() {
		assertTrue(ModelUtil.TRANSFER_PROCESS_SUSPENDED.getState().canTransitTo(TransferState.STARTED));
		assertTrue(ModelUtil.TRANSFER_PROCESS_SUSPENDED.getState().canTransitTo(TransferState.TERMINATED));
		assertFalse(ModelUtil.TRANSFER_PROCESS_SUSPENDED.getState().canTransitTo(TransferState.REQUESTED));
		assertFalse(ModelUtil.TRANSFER_PROCESS_SUSPENDED.getState().canTransitTo(TransferState.COMPLETED));
	}

	private void validateJavaObject(TransferProcess javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getState());
	}
}
