package it.eng.connector.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import it.eng.tools.serializer.ToolsSerializer;

class UserDTOTest {

	@Test
	void test() {
		UserDTO userDTO= new UserDTO();
		userDTO.setEmail("test@mail.com");
		userDTO.setFirstName("firstName");
		userDTO.setLastName("lastName");
		userDTO.setRole(Role.ROLE_ADMIN);
		userDTO.setPassword("password");
		assertNotNull(ToolsSerializer.serializePlain(userDTO));
	}

}
