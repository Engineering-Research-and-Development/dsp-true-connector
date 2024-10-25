package it.eng.tools.event.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used when finalizing Contract Negotiation on provider side<br>
 * Creates new Transfer Process with INITIALIZED state and all data needed from Catalog and ContractNegotiation module
 */
@AllArgsConstructor
@Getter
public class InitializeTransferProcessProvider {
	
	private String fileId;
	private String agreementId;
	private String format;

}
