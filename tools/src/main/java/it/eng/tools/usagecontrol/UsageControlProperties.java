package it.eng.tools.usagecontrol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UsageControlProperties {

	@Value("${application.usagecontrol.enabled}")
	private boolean usageControlEnabled;
	
	public boolean usageControlEnabled() {
		return usageControlEnabled;
	}
}
