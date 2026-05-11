package it.eng.dataplane.api;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class DataTransferProtocolTest {

    private final DataTransferProtocol protocol = new DataTransferProtocol() {
        @Override public String transferType() { return "TestType"; }
        @Override public CompletableFuture<DataFlowResult> execute(DataFlow dataFlow) {
            return CompletableFuture.completedFuture(DataFlowResult.success(dataFlow.getProcessId()));
        }
        @Override public void suspend(DataFlow dataFlow) {}
        @Override public void resume(DataFlow dataFlow) {}
        @Override public void terminate(DataFlow dataFlow) {}
    };

    @Test
    void transferTypeReturnsRegisteredType() {
        assertThat(protocol.transferType()).isEqualTo("TestType");
    }

    @Test
    void executeCompletesSuccessfully() throws Exception {
        DataFlow flow = DataFlow.Builder.newInstance()
            .processId("tp-1")
            .transferType("TestType")
            .build();
        DataFlowResult result = protocol.execute(flow).get();
        assertThat(result.isSuccess()).isTrue();
    }
}
