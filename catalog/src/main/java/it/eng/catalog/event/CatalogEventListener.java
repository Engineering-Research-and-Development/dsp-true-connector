package it.eng.catalog.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CatalogEventListener {
	
	private CatalogService catalogService;
	
	public CatalogEventListener(CatalogService catalogService) {
		super();
		this.catalogService = catalogService;
	}

	@EventListener
	public void handleContextStart(Catalog catalog) {
		log.info("Handling context started event. " + catalog.getId());
	}
	
	@EventListener
	public void handleContractNegotationOfferRequestEvent(ContractNegotationOfferRequestEvent offerRequest) {
		log.info("Received event - ContractNegotationOfferRequestEvent");
		catalogService.validateOffer(offerRequest);
	}
	
}
