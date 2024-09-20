package it.eng.negotiation.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * allOf #/definitions/AbstractPolicyRule
 * 
 * definitions/Constraint min 1
 * "required": "odrl:action"
 */
@Getter
@JsonDeserialize(builder = Permission.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Permission {

	@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
	private String assigner;
	
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
	private String assignee;
	
	// not sure if this one is required at all or just optional for permission
	@JsonProperty(DSpaceConstants.ODRL_TARGET)
	private Object target;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_ACTION)
	private Object action;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_CONSTRAINT)
	private List<Constraint> constraint;
	
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
		public Builder target(Object target) {
			permission.target = target;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_ACTION)
		public Builder action(Object action) {
			permission.action = action;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_CONSTRAINT)
		public Builder constraint(List<Constraint> constraint) {
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
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof Permission)) {
			return false;
		}
		
		// typecast o to ContractNegotiationEventMessage so that we can compare data members 
		Permission permission = (Permission) obj;
				
		if(!StringUtils.equals(this.assignee, permission.getAssignee())) {
			return false;
		}
		if(!StringUtils.equals(this.assigner, permission.getAssigner())) {
			return false;
		}
		String thisTarget = extractTarget(this.target);
		String anotherTarget = extractTarget(permission.getTarget());
		if(!StringUtils.equals(thisTarget, anotherTarget)) {
			return false;
		}
		Action thisAction = extractAction(this.getAction());
		Action anotherAction = extractAction(permission.getAction());
		if (thisAction != anotherAction) {
            return false;
		}
		if(!Arrays.equals(this.constraint.toArray(), permission.getConstraint().toArray())){
            return false;
		}
		
		return true;
	}
	
	public static Action extractAction(Object actionObj) {
		Action action = null;
		if(actionObj instanceof Map) {
			action = Action.fromAction((String)((Map)actionObj).get(DSpaceConstants.ID));
		} else if(actionObj instanceof Reference) {
			action = Action.fromAction(((Reference)actionObj).getId());
		} else if(actionObj instanceof String) {
			action = Action.fromAction((String) actionObj);
		} else {
			action = (Action) actionObj;
		}
		return action;
	}
	
	public static String extractTarget(Object targetObj) {
		String target = null;
		if(targetObj instanceof Map) {
			target = (String) ((Map)targetObj).get(DSpaceConstants.ID);
		} else if(targetObj instanceof Reference) {
			target = ((Reference) targetObj).getId();
		} else {
			target = (String) targetObj ;
		} 
		return target;
	}

}
