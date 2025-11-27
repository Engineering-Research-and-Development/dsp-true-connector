---
applyTo: "**/model/**/*.java"
---

# Model Class Creation Guidelines

_Last updated: 2025-10-24_

This document provides instructions and best practices for creating new model classes in this repository. Follow these
guidelines to ensure consistency, maintainability, and integration with the existing frameworks and tools.

---

## 1. Annotations

- **Lombok**: Use `@Getter`, `@NoArgsConstructor`, as needed to reduce boilerplate.
- **Persistence**: For MongoDB entities, use `@Document(collection = "your_collection")` and `@Id` for the primary key.
- **Jackson**: Use `@JsonProperty`, `@JsonIgnore`, `@JsonDeserialize`, `@JsonPropertyOrder` for JSON mapping.
- **Validation**: Use Jakarta validation annotations (e.g., `@NotNull`) for required fields.

## 2. Class Structure

- Fields should be `private` and `final` where possible.
- Implement `Serializable` for data transfer objects.
- Define `serialVersionUID` for serializable classes.
- Use the builder pattern for object creation, with a static inner `Builder` class if needed.

## 3. Field Handling

- Use `@JsonIgnore` for sensitive or internal fields (e.g., passwords).
- Use `@JsonPropertyOrder` to specify JSON field order if required.
- Use `@DBRef` for references to other MongoDB documents.

## 4. Constructor and Instantiation

- Use `@NoArgsConstructor(access = AccessLevel.PRIVATE)` to enforce builder usage.
- Provide a builder for complex objects.

## 5. Validation

- Annotate required fields with `@NotNull` or other relevant validation annotations.

## 6. Documentation

- Add Javadoc comments for the class and all public methods.

---

## 7. Junit Testing

- Create corresponding JUnit test classes for each model class.
- Use assertions to validate the behavior of getters, setters, and any custom methods.
- Ensure coverage for serialization and deserialization processes.

## Example Template

```java
package your.module.model;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.ValidationException;

import java.io.Serializable;
import java.io.Serial;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = YourModel.Builder.class)
@Document(collection = "your_collection")
@JsonPropertyOrder({"field1", "field2"})
public class YourModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    private String field1;

    private String field2;

    // ...other fields and methods...

    @JsonIgnore
    @CreatedDate
    private Instant issued;

    @JsonIgnore
    @CreatedBy
    private String createdBy;

    @JsonIgnore
    @LastModifiedBy
    private String lastModifiedBy;

    @JsonIgnore
    @Version
    @Field("version")
    private Long version;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final YourModel model;

        private Builder() {
            message = new YourModel();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder field1(String field1) {
            message.field1 = field1;
            return this;
        }

        public Builder field2(String field2) {
            message.field2 = field2;
            return this;
        }

        public YourModel build() {
            Set<ConstraintViolation<Catalog>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(catalog);
            if (violations.isEmpty()) {
                return message;
            }
            throw new ValidationException("YourModel - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }
}

```

---

By following these instructions, you will help maintain a high standard of code quality and consistency across all model
classes in this project.
