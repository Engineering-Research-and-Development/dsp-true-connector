package it.eng.tools.event.applicationproperties;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ApplicationPropertiesEventListener {

	@EventListener
	public void pplicationPropertyChanged(ApplicationPropertyChangeEvent applicationPropertyChangeEvent) {
		StringBuilder sb = new StringBuilder("Property '{").append(applicationPropertyChangeEvent.getNewValue().getKey()).append("}' changed!")
				.append(" Old value '{").append(applicationPropertyChangeEvent.getOldValue().getValue()).append("}'")
				.append(" New value '{").append(applicationPropertyChangeEvent.getNewValue().getValue()).append("}'");
//				.append(" By user '{").append(applicationPropertyChangeEvent.getAuthentication().getPrincipal()).append("}'");
		log.info(sb.toString());
	}
}
