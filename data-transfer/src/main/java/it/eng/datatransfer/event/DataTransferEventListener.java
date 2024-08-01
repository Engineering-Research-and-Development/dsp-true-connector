package it.eng.datatransfer.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import it.eng.datatransfer.model.TransferSuspensionMessage;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DataTransferEventListener {
	
	@EventListener
	public void handleTransferProcessChange(TransferProcessChangeEvent transferProcessEvent) {
		log.info("Transfering process {} from state '{}' to '{}'", 
				transferProcessEvent.getOldTransferProcess().getId(), transferProcessEvent.getOldTransferProcess().getState(),
				transferProcessEvent.getNewTransferProcess().getState());
	}
	
	@EventListener
	public void handleTransferSuspensionMessage(TransferSuspensionMessage transferSuspensionMessage) {
		log.info("Suspending transfer with code {} and reason {}", transferSuspensionMessage.getCode(), transferSuspensionMessage.getReason());
	}

}
