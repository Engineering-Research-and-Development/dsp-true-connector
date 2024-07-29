package it.eng.tools.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
				.addModule(new JavaTimeModule())
				.build();

		jsonMapper = JsonMapper.builder()
				.serializationInclusion(Include.NON_NULL)
				.serializationInclusion(Include.NON_EMPTY)
				.configure(SerializationFeature.INDENT_OUTPUT, true)
				.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.addModule(new JavaTimeModule())
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
	 * Convert object to jsonNode, without annotations. Used in tests
	 * @param toSerialize
	 * @return JsonNode
	 */
	public static JsonNode serializePlainJsonNode(Object toSerialize) {
		return jsonMapperPlain.convertValue(toSerialize, JsonNode.class);
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
	 * Convert object to JsonNode with prefixes. Used in tests
	 * @param toSerialize
	 * @return
	 */
	public static JsonNode serializeProtocolJsonNode(Object toSerialize) {
		return jsonMapper.convertValue(toSerialize, JsonNode.class);
	}

	@SuppressWarnings("unchecked")
	public static List<JsonNode> serializeProtocolListOfJsonNode(Object toSerialize) {
		return jsonMapper.convertValue(toSerialize, List.class);
	}

	/**
	 * Convert Dataspace json (with prefixes) to java object, performs validation for @context and @type before converting to java
	 * Enforce validation for mandatory fields
	 * @param <T>
	 * @param jsonNode
	 * @param clazz
	 * @return
	 */
	public static <T> T deserializeProtocol(JsonNode jsonNode, Class<T> clazz) {
		T obj = jsonMapper.convertValue(jsonNode, clazz);
		Set<ConstraintViolation<T>> violations = validator.validate(obj);
		if(violations.isEmpty()) {
			return obj;
		}
		throw new ValidationException(
				violations
				.stream()
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.collect(Collectors.joining(",")));
	}

}
