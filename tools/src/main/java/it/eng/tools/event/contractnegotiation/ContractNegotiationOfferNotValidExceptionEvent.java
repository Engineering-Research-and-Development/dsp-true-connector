package it.eng.tools.event.contractnegotiation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used to send exception from catalog module to negotiation for further propagation</br>
 *
 */
@AllArgsConstructor
@Getter
public class ContractNegotiationOfferNotValidExceptionEvent {
	
	private String consumerPid;
	private String providerPid;
	private Exception exception;

}
