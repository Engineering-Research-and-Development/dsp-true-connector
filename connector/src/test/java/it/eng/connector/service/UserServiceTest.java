package it.eng.connector.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.model.PasswordValidationResult;
import it.eng.connector.model.User;
import it.eng.connector.model.UserDTO;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.TestUtil;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
	
	private static final String USER_ID = "1234";
	private static final String USER = "user@test.com";
	
	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder encoder;
	@Mock
	private PasswordCheckValidator passwordValidator;
	
	@InjectMocks
	private UserService userService;

	@Mock
	private UserDTO userDTO;
	@Mock
	private PasswordValidationResult passwordValidationResult;
	
	@Test
	@DisplayName("Find users")
	void testFindUsers() {
		when(userRepository.findAll()).thenReturn(Arrays.asList(TestUtil.USER));
		Collection<JsonNode> response = userService.findUsers(null);
		assertNotNull(response);
		assertFalse(response.stream().allMatch(jn -> jn.toPrettyString().contains("password")));
		
		// found by email
		when(userRepository.findByEmail(TestUtil.USER.getEmail())).thenReturn(Optional.of(TestUtil.USER));
		response = userService.findUsers(TestUtil.USER.getEmail());
		assertNotNull(response);
		assertFalse(response.stream().allMatch(jn -> jn.toPrettyString().contains("password")));
		
		// not found by email
		when(userRepository.findByEmail("not found")).thenReturn(Optional.empty());
		assertThrows(ResourceNotFoundException.class, () -> userService.findUsers("not found"));
	}

	@Test
	@DisplayName("Create user")
	void createUser() {
		when(userDTO.getEmail()).thenReturn(USER);
		when(userRepository.findByEmail(userDTO.getEmail())).thenReturn(Optional.empty());
		when(passwordValidator.isValid(userDTO.getPassword())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(true);
		
		userService.createUser(userDTO);
		
		verify(userRepository).save(any(User.class));
	}
	
	@Test
	@DisplayName("Create user - user email already exists")
	void createUser_not_found() {
		when(userDTO.getEmail()).thenReturn(USER);
		when(userRepository.findByEmail(userDTO.getEmail())).thenReturn(Optional.of(TestUtil.USER));
		assertThrows(BadRequestException.class, () -> userService.createUser(userDTO));
		
		verify(userRepository, times(0)).save(any(User.class));
	}
	
	@Test
	@DisplayName("Create user - password not valid")
	void createUser_weak_password() {
		when(userDTO.getEmail()).thenReturn(USER);
		when(userRepository.findByEmail(userDTO.getEmail())).thenReturn(Optional.empty());
		when(passwordValidator.isValid(userDTO.getPassword())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(false);
		
		assertThrows(BadRequestException.class, () -> userService.createUser(userDTO));
		
		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user")
	void updateUser() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		when(userDTO.getFirstName()).thenReturn("First Name update");
		when(userDTO.getLastName()).thenReturn("Last Name update");
		
		userService.updateUser(USER_ID, TestUtil.USER.getEmail(), userDTO);
		
		verify(userRepository).save(any(User.class));
	}
	
	@Test
	@DisplayName("Update user - user not found")
	void updateUser_not_found() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
		
		assertThrows(BadRequestException.class, 
				()-> userService.updateUser(USER_ID, TestUtil.USER.getEmail(), userDTO));
		
		verify(userRepository, times(0)).save(any(User.class));
	}
	
	@Test
	@DisplayName("Update user - updading other user than own")
	void updateUser_other_user() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));

		assertThrows(BadRequestException.class, 
				()-> userService.updateUser(USER_ID, "otheruser@mail.com", userDTO));
		
		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user password")
	void updatePassword() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		when(encoder.matches(anyString(), anyString())).thenReturn(true);
		when(userDTO.getPassword()).thenReturn("aaa");
		when(passwordValidator.isValid(anyString())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(true);
		when(userDTO.getNewPassword()).thenReturn("newPassword");
		when(encoder.encode(anyString())).thenReturn("passwordEncoded");
		
		userService.updatePassword(USER_ID, TestUtil.USER.getEmail(), userDTO);
		
		verify(userRepository).save(any(User.class));
	}
	
	@Test
	@DisplayName("Update user password  user not found")
	void updatePassword_not_found() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
		
		assertThrows(BadRequestException.class, 
				()-> userService.updatePassword(USER_ID, USER, userDTO));
		
		verify(userRepository, times(0)).save(any(User.class));
	}
	
	@Test
	@DisplayName("Update user password - old password not match")
	void updatePassword_not_match() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		when(userDTO.getPassword()).thenReturn("aaa");
		when(encoder.matches(anyString(), anyString())).thenReturn(false);
		
		assertThrows(BadRequestException.class, 
				()-> userService.updatePassword(USER_ID, TestUtil.USER.getEmail(), userDTO));
		
		verify(userRepository, times(0)).save(any(User.class));
	}

}
