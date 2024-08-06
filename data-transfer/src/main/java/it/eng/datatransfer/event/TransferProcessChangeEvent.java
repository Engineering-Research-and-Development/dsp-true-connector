package it.eng.datatransfer.event;

import it.eng.datatransfer.model.TransferProcess;
import lombok.Getter;

@Getter
public class TransferProcessChangeEvent {

	private TransferProcess oldTransferProcess;
	private TransferProcess newTransferProcess;
	
	public static class Builder {
		
		private TransferProcessChangeEvent event;
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		private Builder() {
			event = new TransferProcessChangeEvent();
		}
		
		public Builder oldTransferProcess(TransferProcess oldTransferProcess) {
			event.oldTransferProcess = oldTransferProcess;
			return this;
		}
		
		public Builder newTransferProcess(TransferProcess newTransfProcess) {
			event.newTransferProcess = newTransfProcess;
			return this;
		}
		
		public TransferProcessChangeEvent build() {
			return event;
		}
	}
}
