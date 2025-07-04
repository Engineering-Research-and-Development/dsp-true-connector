package it.eng.datatransfer.event;

import it.eng.datatransfer.model.TransferProcess;
import lombok.Getter;

@Getter
public class TransferArtifactEvent {
    private TransferProcess transferProcess;
    private String transferProcessId;
    private boolean isDownload;
    private String message;

    public static class Builder {
        private TransferArtifactEvent event;

        public static Builder newInstance() {
            return new Builder();
        }

        private Builder() {
            event = new TransferArtifactEvent();
        }

        public Builder transferProcess(TransferProcess transferProcess) {
            event.transferProcess = transferProcess;
            return this;
        }

        public Builder transferProcessId(String transferProcessId) {
            event.transferProcessId = transferProcessId;
            return this;
        }

        public Builder isDownload(boolean isDownload) {
            event.isDownload = isDownload;
            return this;
        }

        public Builder message(String message) {
            event.message = message;
            return this;
        }

        public TransferArtifactEvent build() {
            return event;
        }
    }
}
