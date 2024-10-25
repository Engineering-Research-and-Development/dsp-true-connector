package it.eng.tools.event.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used when finalizing Contract Negotiation on consumer side<br>
 * Creates new Transfer Process with INITIALIZED state and all data needed from Catalog and ContractNegotiation module
 */
@AllArgsConstructor
@Getter
public class InitializeTransferProcessConsumer {
	
	private String agreementId;
	private String format;

}
