package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
{
      "@value": "License model does not fit.",
      "@language": "en"
}
 */

@Getter
@JsonDeserialize(builder = Reason.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Reason {

	@JsonProperty(DSpaceConstants.VALUE)
	private String value;
	
	@JsonProperty(DSpaceConstants.LANGUAGE)
	private String language;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private Reason message;

		private Builder() {
			message = new Reason();
		}

		public static Builder newInstance() {
			return new Builder();
		}

		@JsonProperty(DSpaceConstants.VALUE)
		public Builder value(String value) {
			message.value = value;
			return this;
		}

		@JsonProperty(DSpaceConstants.LANGUAGE)
		public Builder language(String language) {
			message.language = language;
			return this;
		}
		
		public Reason build() {
			return message;
		}
	}
}
