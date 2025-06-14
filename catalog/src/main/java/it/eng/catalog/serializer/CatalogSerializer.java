package it.eng.catalog.serializer;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.Offer;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.serializer.InstantDeserializer;
import it.eng.tools.serializer.InstantSerializer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CatalogSerializer {

    private static JsonMapper jsonMapperPlain;
    private static JsonMapper jsonMapper;
    private static Validator validator;

    static {

        SimpleModule instantConverterModule = new SimpleModule();
        instantConverterModule.addSerializer(Instant.class, new InstantSerializer());
        instantConverterModule.addDeserializer(Instant.class, new InstantDeserializer());

        JacksonAnnotationIntrospector ignoreJsonPropertyIntrospector = new JacksonAnnotationIntrospector() {
			private static final long serialVersionUID = 1L;

			@Override
            protected TypeResolverBuilder<?> _findTypeResolver(MapperConfig<?> config, Annotated ann, JavaType baseType) {
				 if (!ann.hasAnnotation(JsonProperty.class)) {  // || !ann.hasAnnotation(JsonValue.class)
	                    return super._findTypeResolver(config, ann, baseType);
	                } else if(ann.hasAnnotation(JsonProperty.class) && ann.getName().equals("getId")) {
	                	return super._findTypeResolver(config, ann, baseType);
	                }
                return StdTypeResolverBuilder.noTypeInfoBuilder();
            }
			
			@Override
			// used when converting from Java to String; must exclude JsonIgnore for ContractNegotiation.id
			protected <A extends Annotation> A _findAnnotation(Annotated ann, Class<A> annoClass) {
				//  annoClass == JsonValue.class - enum returned without prefix for plain
				if ((annoClass == JsonProperty.class && !ann.getName().equals("id")) || annoClass == JsonIgnore.class 
						|| annoClass == JsonValue.class) {
					return null;
				}
				return super._findAnnotation(ann, annoClass);
			}
			
        };
        
        jsonMapperPlain = JsonMapper.builder()
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addModules(new JavaTimeModule(), instantConverterModule)
                .annotationIntrospector(ignoreJsonPropertyIntrospector)
                .build();

        jsonMapper = JsonMapper.builder()
                .serializationInclusion(Include.NON_NULL)
                .serializationInclusion(Include.NON_EMPTY)
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addModules(new JavaTimeModule(), instantConverterModule)
                .build();

        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * Serialize java object to json.
     *
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
     * Convert object to jsonNode, without annotations.<br>
     * Used in tests
     *
     * @param toSerialize
     * @return JsonNode
     */
    public static JsonNode serializePlainJsonNode(Object toSerialize) {
        return jsonMapperPlain.convertValue(toSerialize, JsonNode.class);
    }

    /**
     * Converts json string (plain) to java object.
     *
     * @param <T>             Type of class
     * @param jsonStringPlain json string
     * @param clazz
     * @return Java object converted from json
     */
    public static <T> T deserializePlain(String jsonStringPlain, Class<T> clazz) {
        try {
            T obj = jsonMapperPlain.readValue(jsonStringPlain, clazz);
            Set<ConstraintViolation<T>> violations = validator.validate(obj);
            if (violations.isEmpty()) {
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
     * Converts json string (plain) to java object.
     *
     * @param <T>             Type of class
     * @param jsonNode jsonNode
     * @param clazz
     * @return Java object converted from json
     */
    public static <T> T deserializePlain(JsonNode jsonNode, Class<T> clazz) {
            T obj = jsonMapperPlain.convertValue(jsonNode, clazz);
            Set<ConstraintViolation<T>> violations = validator.validate(obj);
            if (violations.isEmpty()) {
                return obj;
            }
            throw new ValidationException(
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
    }
    
    public static <T> T deserializePlain(String jsonStringPlain, TypeReference<T> typeRef) {
		try {
			T obj = jsonMapperPlain.readValue(jsonStringPlain, typeRef);
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
     * Serialize java object to json compliant with Dataspace protocol (contains prefixes for json fields).
     *
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
     * Convert object to JsonNode with prefixes. <br>
     * Used in tests
     *
     * @param toSerialize
     * @return JsonNode
     */
    public static JsonNode serializeProtocolJsonNode(Object toSerialize) {
        return jsonMapper.convertValue(toSerialize, JsonNode.class);
    }

    /**
     * Convert Dataspace json (with prefixes) to java object.<br>
     * Performs validation for @context and @type before converting to java
     * Enforce validation for mandatory fields
     *
     * @param <T> Type of class to deserialize
     * @param jsonNode JsonNode to deserialize
     * @param clazz Class to deserialzie
     * @return Java object
     */
    public static <T> T deserializeProtocol(JsonNode jsonNode, Class<T> clazz) {
        validateProtocol(jsonNode, clazz);
        T obj = jsonMapper.convertValue(jsonNode, clazz);
        Set<ConstraintViolation<T>> violations = validator.validate(obj);
        if (violations.isEmpty()) {
            return obj;
        }
        throw new ValidationException(
                violations
                        .stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining(",")));
    }
    
    public static <T> T deserializeProtocol(String jsonStringPlain, Class<T> clazz) {
        try {
            T obj = jsonMapper.readValue(jsonStringPlain, clazz);
            Set<ConstraintViolation<T>> violations = validator.validate(obj);
            if (violations.isEmpty()) {
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
     * Checks for @context and @type if present and if values are correct.
     *
     * @param <T> typed class
     * @param jsonNode JsonNode
     * @param clazz Class to check
     */
    private static <T> void validateProtocol(JsonNode jsonNode, Class<T> clazz) {
        try {
            Objects.requireNonNull(jsonNode.get(DSpaceConstants.TYPE));
            if(clazz.equals(Offer.class)) {
				if(!Objects.equals(DSpaceConstants.ODRL + clazz.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText())) {
					throw new ValidationException("@type field not correct, expected "+ DSpaceConstants.ODRL + clazz.getSimpleName() + " but was " + jsonNode.get(DSpaceConstants.TYPE).asText());
				}
			}  else {
				if (clazz.equals(Catalog.class) || clazz.equals(Dataset.class) || clazz.equals(DataService.class)) {
					if (!Objects.equals(DSpaceConstants.DCAT + clazz.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText())) {
						throw new ValidationException("@type field not correct, expected " + DSpaceConstants.DSPACE + clazz.getSimpleName() + " but was " + jsonNode.get(DSpaceConstants.TYPE).asText());
					}
				} else {
					if (!Objects.equals(DSpaceConstants.DSPACE + clazz.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText())) {
						throw new ValidationException("@type field not correct, expected " + DSpaceConstants.DSPACE + clazz.getSimpleName() + " but was " + jsonNode.get(DSpaceConstants.TYPE).asText());
					}
				}
			}
            //if(!(Distribution.class.isInstance(clazz) || DataService.class.isInstance(clazz))) {
            //if(!(clazz.isInstance(Distribution.class) || clazz.isInstance(DataService.class))) {
            // skip context check if not one of following
            if (!(clazz.equals(Distribution.class) || clazz.equals(DataService.class) || clazz.equals(Offer.class))) {
                Objects.requireNonNull(jsonNode.get(DSpaceConstants.CONTEXT));
                if (!Objects.equals(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText())) {
                    throw new ValidationException("@contexxt field not valid - was " + jsonNode.get(DSpaceConstants.CONTEXT).asText());
                }
            }
        } catch (NullPointerException npe) {
            throw new ValidationException("Missing mandatory protocol fields @context and/or @type or value not correct");
        }
    }
}
