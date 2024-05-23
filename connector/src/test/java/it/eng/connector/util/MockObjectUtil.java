package it.eng.connector.util;

import it.eng.connector.model.Property;

public class MockObjectUtil {

	private static final String DEFAULT_VALUE = "defaultValue";
	private static final String KEY = "key";
	private static final String VALUE = "value";
	private static final boolean MANDATORY = false;
	
	public static Property PROPERTY = Property.Builder.newInstance()
			.defaultValue(DEFAULT_VALUE)
			.key(KEY)
			.value(VALUE)
			.mandatory(MANDATORY)
			.build();
	
	
	
}
