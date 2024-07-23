package it.eng.tools.util;

import java.time.Instant;

import it.eng.tools.model.ApplicationProperty;

public class MockObjectUtil {

	public static final String CREATOR = "admin@mail.com";
	public static final Instant NOW = Instant.now();

	public static final ApplicationProperty PROPERTY = ApplicationProperty.Builder.newInstance()
			//.createdBy(CREATOR)
			//.issued(NOW)
			.key("Sample key")
			//.lastModifiedBy(CREATOR)
			.mandatory(false)
			//.modified(NOW)
			.sampleValue("Sample samplevalue")
			.value("Sample value")
			//.version(0L)
			.build();
	
	public static final ApplicationProperty APPLICATION_PROPERTY_FOR_UPDATE = ApplicationProperty.Builder.newInstance()
			.createdBy(CREATOR)
			.issued(NOW)
			.key("Sample key")
			.lastModifiedBy(CREATOR)
			.mandatory(false)
			.modified(NOW)
			.sampleValue("Sample samplevalue")
			.value("Sample value")
			.version(0L)
			.build();
	
	public static final ApplicationProperty OLD_APPLICATION_PROPERTY_FOR_UPDATE = ApplicationProperty.Builder.newInstance()
			.createdBy(CREATOR)
			.issued(NOW)
			.key("Sample key")
			.lastModifiedBy(CREATOR)
			.mandatory(false)
			.modified(NOW)
			.sampleValue("Sample samplevalue")
			.value("Old sample value")
			.version(0L)
			.build();
	
}
