package it.eng.tools.event.applicationproperties;

import org.springframework.security.core.Authentication;

import it.eng.tools.model.ApplicationProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ApplicationPropertyChangeEvent {

	private ApplicationProperty oldValue;
	private ApplicationProperty newValue;
	private Authentication authentication;
}
