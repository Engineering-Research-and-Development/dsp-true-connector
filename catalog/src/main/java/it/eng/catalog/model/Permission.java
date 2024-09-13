package it.eng.catalog.model;

import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(exclude = {"target", "assigner", "assignee"}) // requires for offer check in negotiation flow
@JsonDeserialize(builder = Permission.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Permission implements Serializable {

	private static final long serialVersionUID = -6221623714296723036L;

	@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
	private String assigner;
	
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
	private String assignee;
	
	// not sure if this one is required at all or just optional for permission
	@JsonProperty(DSpaceConstants.ODRL_TARGET)
	private String target;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_ACTION)
	private Action action;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_CONSTRAINT)
	private Set<Constraint> constraint;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private Permission permission;

		private Builder() {
			permission = new Permission();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
		public Builder assigner(String assigner) {
			permission.assigner = assigner;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
		public Builder assignee(String assignee) {
			permission.assignee = assignee;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_TARGET)
		public Builder target(String target) {
			permission.target = target;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_ACTION)
		public Builder action(Action action) {
			permission.action = action;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_CONSTRAINT)
		@JsonDeserialize(as = Set.class)
		public Builder constraint(Set<Constraint> constraint) {
			permission.constraint = constraint;
			return this;
		}
		
		public Permission build() {
			Set<ConstraintViolation<Permission>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(permission);
			if(violations.isEmpty()) {
				return permission;
			}
			throw new ValidationException("Permission - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", ")));
		}
	}
}
