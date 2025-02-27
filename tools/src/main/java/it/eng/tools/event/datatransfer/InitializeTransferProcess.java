package it.eng.tools.event.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used when finalizing Contract Negotiation.<br>
 * Creates new Transfer Process with INITIALIZED state and all data needed from Catalog and ContractNegotiation module
 */
@AllArgsConstructor
@Getter
public class InitializeTransferProcess {
	
	private String callbackAddress;
	private String agreementId;
	private String datasetId;
	private String role;

}
