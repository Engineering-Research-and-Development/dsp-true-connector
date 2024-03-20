package it.eng.datatransfer.model;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;

public class Serializer {

	private static JsonMapper jsonMapperPlain;
	private static JsonMapper jsonMapper;
	private static Validator validator;
	
	static {
		jsonMapperPlain = JsonMapper.builder()
				.configure(MapperFeature.USE_ANNOTATIONS, false)
				.serializationInclusion(Include.NON_NULL)
				.serializationInclusion(Include.NON_EMPTY)
				.configure(SerializationFeature.INDENT_OUTPUT, true)
				.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.build();
		
		jsonMapper = JsonMapper.builder()
				.serializationInclusion(Include.NON_NULL)
				.serializationInclusion(Include.NON_EMPTY)
				.configure(SerializationFeature.INDENT_OUTPUT, true)
				.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
//			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.build();
		
		validator = Validation.buildDefaultValidatorFactory().getValidator();
	}
	
	/**
	 * Serialize java object to json
	 * @param toSerialize
	 * @return Json string - plain
	 */
	public static String serializePlain(Object toSerialize) {
		try {
			return jsonMapperPlain.writeValueAsString(toSerialize);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Converts json string (plain) to java object
	 * @param <T> Type of class
	 * @param jsonStringPlain json string
	 * @param clazz
	 * @return Java object converted from json
	 */
	public static <T> T deserializePlain(String jsonStringPlain, Class<T> clazz) {
		try {
			T obj = jsonMapperPlain.readValue(jsonStringPlain, clazz);
			 Set<ConstraintViolation<T>> violations = validator.validate(obj);
			if(violations.isEmpty()) {
				return obj;
			}
			throw new ValidationException(
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Serialize java object to json compliant with Dataspace protocol (contains prefixes for json fields)
	 * @param toSerialize java object to serialize
	 * @return Json string - with Dataspace prefixes
	 */
	public static String serializeProtocol(Object toSerialize) {
		try {
			return jsonMapper.writeValueAsString(toSerialize);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Convert Dataspace json (with prefixes) to java object
	 * @param <T> Type of class
	 * @param jsonStringProtocol
	 * @param clazz Type of class
	 * @return Java object converted from json
	 */
	public static <T> T deserializeProtocol(String jsonStringProtocol, Class<T> clazz) {
		try {
			T obj = jsonMapper.readValue(jsonStringProtocol, clazz);
			 Set<ConstraintViolation<T>> violations = validator.validate(obj);
			if(violations.isEmpty()) {
				return obj;
			}
			throw new ValidationException(
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
}
