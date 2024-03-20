package it.eng.catalog.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import it.eng.catalog.model.Catalog;

@Component
public class CatalogEventListener {

	@EventListener
	public void handleContextStart(Catalog catalog) {
		System.out.println("Handling context started event. " + catalog.getId());
	}
}
