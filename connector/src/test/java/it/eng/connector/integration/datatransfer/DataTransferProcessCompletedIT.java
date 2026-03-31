package it.eng.connector.integration.datatransfer;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.repository.TemporaryBucketUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DataTransferProcessCompletedIT extends BaseIntegrationTest {
// STARTED -> COMPLETED

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Autowired
    private TemporaryBucketUserRepository temporaryBucketUserRepository;

    @AfterEach
    public void cleanup() {
        transferProcessRepository.deleteAll();
        temporaryBucketUserRepository.deleteAll();
    }

    // Provider
    @Test
    @DisplayName("Complete transfer process - from started")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_provider() throws Exception {

        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        transferProcessRepository.save(transferProcessStarted);

        TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
                .consumerPid(transferProcessStarted.getConsumerPid())
                .providerPid(transferProcessStarted.getProviderPid())
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/transfers/" + transferCompletionMessage.getProviderPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferCompletionMessage))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk());
//    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));

        ResultActions transferProcessStartedAction = mockMvc.perform(
                get("/transfers/" + transferProcessStarted.getProviderPid())
                        .contentType(MediaType.APPLICATION_JSON));
        // check if status is COMPLETED
        transferProcessStartedAction.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = transferProcessStartedAction.andReturn().getResponse().getContentAsString();
        TransferProcess transferProcessStarted2 = TransferSerializer.deserializeProtocol(response, TransferProcess.class);
        assertNotNull(transferProcessStarted2);
        assertEquals(TransferState.COMPLETED, transferProcessStarted2.getState());
    }

    @Test
    @DisplayName("Complete transfer process - not_found")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_transfer_not_found() throws Exception {
        TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
                .providerPid(createNewId())
                .consumerPid(createNewId())
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/transfers/" + transferProcessStarted.getProviderPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferProcessStarted))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest());

        String response = result.andReturn().getResponse().getContentAsString();
        TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
        assertNotNull(transferError);
    }

    @Test
    @DisplayName("Complete transfer process - provider - invalid message type")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_provider_transfer_invalid_msg() throws Exception {
        TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
                .consumerPid(createNewId())
                .agreementId("agreement_id")
                .format(DataTransferFormat.HTTP_PULL.format())
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/transfers/" + transferRequestMessage.getConsumerPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferRequestMessage))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Complete transfer process - invalid state")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_invalid_state() throws Exception {
        TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        transferProcessRepository.save(transferProcessRequested);

        TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
                .providerPid(createNewId())
                .consumerPid(createNewId())
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/transfers/" + transferProcessStarted.getProviderPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferProcessStarted))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest());

        String response = result.andReturn().getResponse().getContentAsString();
        TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
        assertNotNull(transferError);
    }

    // Consumer
    @Test
    @DisplayName("Complete transfer process - from started - consumer")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_consumer() throws Exception {
        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        transferProcessRepository.save(transferProcessStarted);

        TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
                .consumerPid(transferProcessStarted.getConsumerPid())
                .providerPid(transferProcessStarted.getProviderPid())
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/consumer/transfers/" + transferCompletionMessage.getConsumerPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferCompletionMessage))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk());

        ResultActions transferProcessStartedAction = mockMvc.perform(
                get("/transfers/" + transferProcessStarted.getProviderPid())
                        .contentType(MediaType.APPLICATION_JSON));
        // check if status is COMPLETED
        transferProcessStartedAction.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = transferProcessStartedAction.andReturn().getResponse().getContentAsString();
        TransferProcess transferProcessCompleted = TransferSerializer.deserializeProtocol(response, TransferProcess.class);
        assertNotNull(transferProcessCompleted);
        assertEquals(TransferState.COMPLETED, transferProcessCompleted.getState());
    }

    @Test
    @DisplayName("Complete transfer process - consumer - not_found")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_consumer_transfer_not_found() throws Exception {
        TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
                .providerPid(createNewId())
                .consumerPid(createNewId())
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/consumer/transfers/" + transferProcessStarted.getConsumerPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferProcessStarted))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest());

        String response = result.andReturn().getResponse().getContentAsString();
        TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
        assertNotNull(transferError);
    }

    @Test
    @DisplayName("Complete transfer process - consumer - invalid msg")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_consumer_transfer_invalid_msg() throws Exception {
        TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
                .consumerPid(createNewId())
                .agreementId("agreement_id")
                .format(DataTransferFormat.HTTP_PULL.format())
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/consumer/transfers/" + transferRequestMessage.getConsumerPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferRequestMessage))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Complete transfer process - consumer - invalid state")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_consumer_invalid_state() throws Exception {
        TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        transferProcessRepository.save(transferProcessRequested);

        TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
                .providerPid(createNewId())
                .consumerPid(createNewId())
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/transfers/" + transferProcessStarted.getConsumerPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferProcessStarted))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest());

        String response = result.andReturn().getResponse().getContentAsString();
        TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
        assertNotNull(transferError);
    }

    @Test
    @DisplayName("Complete transfer process - temporary user deleted after HTTP-PUSH completion")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completeTransferProcess_provider_httpPush_deletesTemporaryUser() throws Exception {
        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .format(DataTransferFormat.HTTP_PUSH.format())
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        transferProcessRepository.save(transferProcessStarted);

        // Simulate the temporary S3 user that is created before HTTP-PUSH data upload
        TemporaryBucketUser temporaryBucketUser = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(transferProcessStarted.getId())
                .accessKey("temp-test-access-key")
                .secretKey("temp-test-secret-key")
                .bucketName("test-bucket")
                .objectKey("test-object-key")
                .build();
        temporaryBucketUserRepository.save(temporaryBucketUser);

        assertTrue(temporaryBucketUserRepository.existsById(transferProcessStarted.getId()),
                "Temporary bucket user should exist before transfer completion");

        TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
                .consumerPid(transferProcessStarted.getConsumerPid())
                .providerPid(transferProcessStarted.getProviderPid())
                .build();

        mockMvc.perform(
                        post("/transfers/" + transferCompletionMessage.getProviderPid() + "/completion")
                                .content(TransferSerializer.serializeProtocol(transferCompletionMessage))
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertFalse(temporaryBucketUserRepository.existsById(transferProcessStarted.getId()),
                "Temporary bucket user should be removed after transfer completion");
    }
}
