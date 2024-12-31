package it.eng.connector.util;

import it.eng.connector.model.Role;
import it.eng.connector.model.User;

public class TestUtil {
	//all values in this class are from the initial_data.json
	public static final String CONNECTOR_USER = "connector@mail.com";
	public static final String ADMIN_USER = "admin@mail.com";
	public static final String API_USER = "admin@mail.com";
	public static final String DATASET_ID = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
	public static final String PROVIDER_PID = "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab";
	public static final String CONSUMER_PID = "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833";
	public static final String USER_ID = "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5";
	
	public static User USER = new User(USER_ID, "first name", "last name", "test@mail.com", "secret", true, false, false, Role.ROLE_ADMIN);

}
