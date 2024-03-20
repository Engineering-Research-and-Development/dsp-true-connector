package it.eng.catalog.model;

import java.util.List;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
	private String target;
	@NonNull
	@JsonProperty(DSpaceConstants.ODRL_ACTION)
	private Action action;
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
		public Builder constraint(List<Constraint> constraint) {
			permission.constraint = constraint;
			return this;
		}
		
		public Permission build() {
			return permission;
		}
	}

}
