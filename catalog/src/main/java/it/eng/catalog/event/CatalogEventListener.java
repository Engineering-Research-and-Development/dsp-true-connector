package it.eng.catalog.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequest;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CatalogEventListener {
	
	@Autowired
	private CatalogService catalogService;

	@EventListener
	public void handleContextStart(Catalog catalog) {
		log.info("Handling context started event. " + catalog.getId());
	}
	
	@EventListener
	public void handleContractNegotationOfferRequest(ContractNegotationOfferRequest offerRequest) {
		log.info("Received event - ContractNegotationOfferRequest");
		catalogService.validateIfOfferIsValid(offerRequest);
	}
}
