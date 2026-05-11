package it.eng.dataplane.api;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class DataTransferProtocolTest {

    private final DataTransferProtocol protocol = new DataTransferProtocol() {
        @Override public String getProtocolId() { return "TestType"; }
        @Override public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
            return CompletableFuture.completedFuture(DataFlowResult.success());
        }
        @Override public CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId) {
            return CompletableFuture.completedFuture(DataFlowResult.success());
        }
        @Override public CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId) {
            return CompletableFuture.completedFuture(DataFlowResult.success());
        }
        @Override public CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId) {
            return CompletableFuture.completedFuture(DataFlowResult.success());
        }
    };

    @Test
    void transferTypeReturnsRegisteredType() {
        assertThat(protocol.getProtocolId()).isEqualTo("TestType");
    }

    @Test
    void executeCompletesSuccessfully() throws Exception {
        DataFlow flow = DataFlow.Builder.newInstance()
            .processId("tp-1")
            .transferType("TestType")
            .build();
        DataFlowResult result = protocol.initiateTransfer(flow).get();
        assertThat(result.isSuccess()).isTrue();
    }
}
