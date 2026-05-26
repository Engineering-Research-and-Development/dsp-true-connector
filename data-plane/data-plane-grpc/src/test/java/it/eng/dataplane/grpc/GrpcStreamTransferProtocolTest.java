package it.eng.dataplane.grpc;

import it.eng.dataplane.api.io.SourceReader;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.grpc.config.GrpcProperties;
import it.eng.dataplane.grpc.model.GrpcSessionState;
import it.eng.dataplane.grpc.model.GrpcStreamSession;
import it.eng.dataplane.grpc.registry.GrpcSessionRegistry;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GrpcStreamTransferProtocol}.
 */
@ExtendWith(MockitoExtension.class)
class GrpcStreamTransferProtocolTest {

    @Mock
    private GrpcSessionRegistry sessionRegistry;
    @Mock
    private SourceReaderRegistry sourceReaderRegistry;
    @Mock
    private GrpcProperties grpcProperties;
    @Mock
    private SourceReader s3SourceReader;

    private GrpcStreamTransferProtocol protocol;

    @BeforeEach
    void setUp() {
        protocol = new GrpcStreamTransferProtocol(sessionRegistry, sourceReaderRegistry, grpcProperties);
    }

    @Test
    @DisplayName("prepare() returns gRPC endpoint metadata for a finite session")
    void prepare_finiteSession_returnsGrpcMetadata() {
        when(grpcProperties.getHost()).thenReturn("dp-grpc");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-finite-1")
                .datasetId("ds-1")
                .dataAddress(Map.of("sourceType", "s3"))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getProcessId()).isEqualTo("tp-finite-1");
        assertThat(response.getDataAddress()).containsEntry("endpointType", "grpc");
        assertThat(response.getDataAddress()).containsEntry("host", "dp-grpc");
        assertThat(response.getDataAddress()).containsEntry("port", "9094");
        assertThat(response.getDataAddress()).containsKey("sessionId");
        assertThat(response.getDataAddress().get("sessionId")).isNotBlank();
        assertThat(response.getDataAddress()).containsEntry("mode", "finite");
    }

    @Test
    @DisplayName("prepare() returns finite=false metadata when finite hint is 'false'")
    void prepare_nonFiniteSession_returnsNonFiniteMode() {
        when(grpcProperties.getHost()).thenReturn("dp-grpc");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-nonfinite-1")
                .datasetId("ds-2")
                .dataAddress(Map.of("sourceType", "s3", "finite", "false"))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getDataAddress()).containsEntry("mode", "non-finite");
    }

    @Test
    @DisplayName("prepare() registers a PREPARED session in the registry")
    void prepare_registersSessionInRegistry() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-reg-1")
                .datasetId("ds-3")
                .dataAddress(Map.of("sourceType", "s3"))
                .build();

        protocol.prepare(message);

        ArgumentCaptor<GrpcStreamSession> captor = ArgumentCaptor.forClass(GrpcStreamSession.class);
        verify(sessionRegistry).register(captor.capture());
        GrpcStreamSession registered = captor.getValue();
        assertThat(registered.getProcessId()).isEqualTo("tp-reg-1");
        assertThat(registered.getDatasetId()).isEqualTo("ds-3");
        assertThat(registered.isFinite()).isTrue();
        assertThat(registered.getState()).isEqualTo(GrpcSessionState.PREPARED);
        assertThat(registered.getSessionId()).isNotBlank();
    }

    @Test
    @DisplayName("prepare() with non-finite flag registers session with finite=false")
    void prepare_nonFiniteSession_registersWithFiniteFalse() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-nf-2")
                .datasetId("ds-4")
                .dataAddress(Map.of("sourceType", "s3", "finite", "false"))
                .build();

        protocol.prepare(message);

        ArgumentCaptor<GrpcStreamSession> captor = ArgumentCaptor.forClass(GrpcStreamSession.class);
        verify(sessionRegistry).register(captor.capture());
        assertThat(captor.getValue().isFinite()).isFalse();
    }

    @Test
    @DisplayName("prepare() with unknown sourceType throws IllegalArgumentException")
    void prepare_unknownSourceType_throwsIllegalArgument() {
        when(sourceReaderRegistry.getReader("unknown")).thenReturn(Optional.empty());

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-bad-1")
                .datasetId("ds-5")
                .dataAddress(Map.of("sourceType", "unknown"))
                .build();

        assertThatThrownBy(() -> protocol.prepare(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No SourceReader available for sourceType: unknown");
    }

    @Test
    @DisplayName("prepare() defaults sourceType to 's3' when dataAddress is absent")
    void prepare_nullDataAddress_defaultsToS3SourceType() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-default-1")
                .datasetId("ds-6")
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getDataAddress()).containsEntry("mode", "finite");
    }

    @Test
    @DisplayName("getProtocolId() returns 'stream:grpc'")
    void getProtocolId_returnsStreamGrpc() {
        assertThat(protocol.getProtocolId()).isEqualTo("stream:grpc");
    }

    @Test
    @DisplayName("terminateTransfer() removes session from registry and returns success")
    void terminateTransfer_removesSessionAndReturnsSuccess() {
        CompletableFuture<DataFlowResult> future = protocol.terminateTransfer("tp-term-1");
        DataFlowResult result = future.join();

        assertThat(result.isSuccess()).isTrue();
        verify(sessionRegistry).removeByProcessId("tp-term-1");
    }

    @Test
    @DisplayName("initiateTransfer() returns failure since streaming is not yet implemented")
    void initiateTransfer_returnsFailure() {
        it.eng.dataplane.api.model.DataFlow dataFlow = it.eng.dataplane.api.model.DataFlow.Builder.newInstance()
                .processId("tp-start-1")
                .transferType("stream:grpc")
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotBlank();
    }
}
