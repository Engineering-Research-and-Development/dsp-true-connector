package it.eng.connector.model;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.Serializer;

class UserDTOTest {

	@Test
	void test() {
		UserDTO userDTO= new UserDTO();
		userDTO.setEmail("test@mail.com");
		userDTO.setFirstName("firstName");
		userDTO.setLastName("lastName");
		userDTO.setRole(Role.ROLE_ADMIN);
		userDTO.setPassword("password");
		System.out.println(Serializer.serializePlain(userDTO));
	}

}
