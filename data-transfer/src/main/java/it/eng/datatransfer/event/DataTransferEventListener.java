package it.eng.datatransfer.event;

import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DataTransferEventListener {
	
	private final ApplicationEventPublisher publisher;
	private final TransferRequestMessageRepository transferRequestMessageRepository;
	
	public DataTransferEventListener(ApplicationEventPublisher publisher, TransferRequestMessageRepository transferRequestMessageRepository) {
		super();
		this.publisher = publisher;
		this.transferRequestMessageRepository = transferRequestMessageRepository;
	}

	@EventListener
	public void handleTransferProcessChange(TransferProcessChangeEvent transferProcessEvent) {
		log.info("Transfering process {} from state '{}' to '{}'", 
				transferProcessEvent.getOldTransferProcess().getId(), transferProcessEvent.getOldTransferProcess().getState(),
				transferProcessEvent.getNewTransferProcess().getState());
	}
	
	@EventListener
	public void handleTransferStartMessage(TransferStartMessage transferStartMessage) {
		log.info("Transfer Start message event received");
		Optional<TransferRequestMessage> transferRequestMessage = transferRequestMessageRepository.findByConsumerPid(transferStartMessage.getConsumerPid());
		if(transferRequestMessage.isPresent() && transferRequestMessage.get().getFormat().equals("example:SFTP")) {
			log.info("Publishing event to start SFTP server...");
			publisher.publishEvent(new StartFTPServerEvent());
		}
	}
	
	@EventListener
	public void handleTransferSuspensionMessage(TransferSuspensionMessage transferSuspensionMessage) {
		log.info("Suspending transfer with code {} and reason {}", transferSuspensionMessage.getCode(), transferSuspensionMessage.getReason());
		Optional<TransferRequestMessage> transferRequestMessage = transferRequestMessageRepository.findByConsumerPid(transferSuspensionMessage.getConsumerPid());
		if(transferRequestMessage.isPresent() && transferRequestMessage.get().getFormat().equals("example:SFTP")) {
			publisher.publishEvent(new StopFTPServerEvent());
		}
	}
	
	@EventListener
	public void handleTransferCompletionMessage(TransferCompletionMessage transferCompletionMessage) {
		log.info("Completeing transfer with consumerPid {} and providerPid {}", transferCompletionMessage.getConsumerPid(), transferCompletionMessage.getProviderPid());
		Optional<TransferRequestMessage> transferRequestMessage = transferRequestMessageRepository.findByConsumerPid(transferCompletionMessage.getConsumerPid());
		if(transferRequestMessage.isPresent() && transferRequestMessage.get().getFormat().equals("example:SFTP")) {
			publisher.publishEvent(new StopFTPServerEvent());
		}
	}

}
