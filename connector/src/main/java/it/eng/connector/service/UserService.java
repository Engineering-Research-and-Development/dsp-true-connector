package it.eng.connector.service;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.model.PasswordValidationResult;
import it.eng.connector.model.User;
import it.eng.connector.model.UserDTO;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.serializer.ToolsSerializer;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder encoder;
	private final PasswordCheckValidator passwordValidator;

	public UserService(UserRepository userRepository, PasswordEncoder encoder, PasswordCheckValidator passwordValidator) {
		super();
		this.userRepository = userRepository;
		this.encoder = encoder;
		this.passwordValidator = passwordValidator;
	}
	
	public Collection<JsonNode> findUsers(String email) throws ResourceNotFoundException {
		if(StringUtils.isNotBlank(email)) {
			User user = userRepository.findByEmail(email).orElseThrow(ResourceNotFoundException::new);
			return Collections.singletonList(ToolsSerializer.serializePlainJsonNode(user));
//					.ifPresentOrElse(u -> { 
//						Serializer.serializePlainJsonNode(u);
//					}, () -> { throw new IllegalStateException(); });
//					.stream()
//					.map(u -> Serializer.serializePlainJsonNode(u))
//					.collect(Collectors.toList());
		} 
		return userRepository.findAll()
				.stream()
				.map(u -> ToolsSerializer.serializePlainJsonNode(u))
				.collect(Collectors.toList());
	}

	public JsonNode createUser(UserDTO userDTO) {
		// check if user exists
		userRepository.findByEmail(userDTO.getEmail())
			.ifPresent(u -> {
				throw new BadRequestException("User with email already exists");
				});
		PasswordValidationResult validationResult = passwordValidator.isValid(userDTO.getPassword());
		if(validationResult.isValid()) {
			User user = new User(createNewPid(), userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(), 
					encoder.encode(userDTO.getPassword()), true, false, false, userDTO.getRole());
			User u = userRepository.save(user);
			return ToolsSerializer.serializePlainJsonNode(u);
		} else {
			throw new BadRequestException(validationResult.getViolations().stream().collect(Collectors.joining(", ")));
		}
	}
	
	public JsonNode updateUser(String id, String loggedInUser, UserDTO userDTO) {
		// check if updating password for own user
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("User not found"));
		
		if(user.getEmail().equals(loggedInUser)) {
				user.setFirstName(userDTO.getFirstName() != null ? userDTO.getFirstName() : user.getFirstName());
				user.setLastName(userDTO.getLastName() != null ? userDTO.getLastName() : user.getLastName());
				userRepository.save(user);
				return ToolsSerializer.serializePlainJsonNode(user);
		} else {
			log.error("Not allowed to change other user email");
			throw new BadRequestException("Not allowed to change other user email");
		}
	}

	public JsonNode updatePassword(String id, String loggedInUser, UserDTO userDTO) {
		// check if updating password for own user
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("User not found"));
		
		if(user.getEmail().equals(loggedInUser)) {
			if(encoder.matches(userDTO.getPassword(), user.getPassword())) {
				// current password matches with old provided
				
				PasswordValidationResult validationResult = passwordValidator.isValid(userDTO.getNewPassword());
				if(validationResult.isValid()) {
					user.setPassword(encoder.encode(userDTO.getNewPassword()));
					userRepository.save(user);
					return ToolsSerializer.serializePlainJsonNode(user);
				} else {
					log.warn("Password not valid with sthength check");
					throw new BadRequestException(validationResult.getViolations().stream().collect(Collectors.joining(", ")));
				}
			} else {
				throw new BadRequestException("Old password does not match");
			}
		} else {
			log.error("Not allowed to change other user email");
			throw new BadRequestException("Not allowed to change other user email");
		}
	}
	
	  private String createNewPid() {
	        return "urn:uuid:" + UUID.randomUUID();
	    }
}
