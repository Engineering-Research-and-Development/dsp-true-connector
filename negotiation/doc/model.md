# Contract Negotiation Model

Classes used to represent Json document are located in model package.

General rule for creating this classes are following:
  - if possible, class should extend AbstractNegotiationModel, which has @context, @type and providerPid as mandatory fields, since those fields are mandatory for almost all Json documents
  - annotations like @JsonProperty, @JsonPOJOBuilder and @JsonSetter are used to modify how java pojo class is serialized to protocol format (using prefixes like dpsace:, odrl: and such). For more details check Serializer class.
  - constructor should be private (handled by annotation)
  - jakarta.validation.constraints.NotNull annotation should be set on mandatory fields
  - builder should be used as patter for creating new instances
  - build method should invoke code like following, enforcing NotNull validation:

  ```
  Set<ConstraintViolation<T>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(object);
			if(violations.isEmpty()) {
				return object;
			}
			throw new ValidationException(
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
		}
  ```
  - classes should be covered with junit test, at minimal to check if builder is working correctly, checking that NotNull annotation and builder is invoked correctly. One method to create valid instance and another to validate that ValidationException will be thrown when mandatory fields are missing. Developer should also add other junit tests, id applicable. E.g. to check serialize/deserialize to protocol (using prefixes) and serialize/deserialize to plain (without prefixes) 
   