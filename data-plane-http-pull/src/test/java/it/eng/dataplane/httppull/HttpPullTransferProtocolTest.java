package it.eng.dataplane.httppull;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.TenantBucketResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HttpPullTransferProtocol}.
 */
@ExtendWith(MockitoExtension.class)
class HttpPullTransferProtocolTest {

    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private TenantBucketResolver tenantBucketResolver;

    private HttpPullTransferProtocol protocol;

    // Synchronous executor for testing — runs tasks immediately in the calling thread
    private final Executor syncExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        protocol = new HttpPullTransferProtocol(
            s3ClientService,
            s3Properties,
            tenantBucketResolver,
            syncExecutor
        );
    }

    @Test
    void protocolIdIsHttpDataPull() {
        assertThat(protocol.getProtocolId()).isEqualTo("HttpData-PULL");
    }

    @Test
    void initiateTransferReturnsFailureWhenEndpointMissing() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-1")
            .transferType("HttpData-PULL")
            .dataAddress(Map.of()) // no endpoint key
            .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("endpoint");
    }

    @Test
    void initiateTransferReturnsFailureWhenDataAddressIsNull() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-2")
            .transferType("HttpData-PULL")
            .dataAddress(null)
            .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("endpoint");
    }

    @Test
    void suspendTransferReturnsFailure() throws Exception {
        CompletableFuture<DataFlowResult> resultFuture = protocol.suspendTransfer("df-1");
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("suspend not supported");
    }

    @Test
    void resumeTransferReturnsFailure() throws Exception {
        CompletableFuture<DataFlowResult> resultFuture = protocol.resumeTransfer("df-1");
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("resume not supported");
    }

    @Test
    void terminateTransferReturnsSuccess() throws Exception {
        CompletableFuture<DataFlowResult> resultFuture = protocol.terminateTransfer("df-1");
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isTrue();
    }
}
