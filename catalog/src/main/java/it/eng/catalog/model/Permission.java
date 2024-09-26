package it.eng.catalog.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
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
import lombok.extern.slf4j.Slf4j;

@Getter
//@EqualsAndHashCode(exclude = {"target", "assigner", "assignee"}) // requires for offer check in negotiation flow
@JsonDeserialize(builder = Permission.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class Permission implements Serializable {

	private static final long serialVersionUID = -6221623714296723036L;

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
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof Permission)) {
			log.debug("Comparing object not instance of Permission");
			return false;
		}
		
		// typecast o to ContractNegotiationEventMessage so that we can compare data members 
		Permission permission = (Permission) obj;
				
		if(!StringUtils.equals(this.assignee, permission.getAssignee())) {
			log.debug("Permission assignee not equal");
			return false;
		}
		if(!StringUtils.equals(this.assigner, permission.getAssigner())) {
			log.debug("Permission assigner not equal");
			return false;
		}
		String thisTarget = extractTarget(this.target);
		String anotherTarget = extractTarget(permission.getTarget());
		if(!StringUtils.equals(thisTarget, anotherTarget)) {
			log.debug("Permission target not equal");
			return false;
		}
		Action thisAction = extractAction(this.getAction());
		Action anotherAction = extractAction(permission.getAction());
		if (thisAction != anotherAction) {
			log.debug("Permission action not equal");
            return false;
		}
		
		if (this.constraint.size() != permission.getConstraint().size()) {
			log.debug("Permission constraint size not equal");
			return false;
		}
		if(this.constraint.containsAll(permission.getConstraint())){
			log.debug("Permission constraint contains all constrains from check");
            return true;
		}
		
		if (Constraint.compareCollection(this.constraint, permission.getConstraint())) {
			return true;
		}
		
		return false;
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
	
	public static boolean compareCollection (Collection<Permission> c1, Collection<Permission> c2) {
		for (Iterator<Permission> i1 = c1.iterator(); i1.hasNext();) {
			Permission p1 = (Permission) i1.next();
			for (Iterator<Permission> i2 = c2.iterator(); i2.hasNext();) {
				Permission p2 = (Permission) i2.next();
				if (p1.equals(p2)) {
					break;
				} else if (i2.hasNext() == false){
					return false;
				}
			}
		}
		return true;
	}
}
