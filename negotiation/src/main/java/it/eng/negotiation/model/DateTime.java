package it.eng.negotiation.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@JsonDeserialize(builder = DateTime.Builder.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DateTime {

	/*
{
      "@value": "2023-01-01T01:00:00Z",
      "@type": "xsd:dateTime"
}
	 */

	protected String value;

	@JsonPOJOBuilder(withPrefix = "")
	public static class Builder {

		private DateTime message;

		private Builder() {
			message = new DateTime();
		}

		public static Builder newInstance() {
			return new Builder();
		}

		public Builder value(String value) {
			message.value = value;
			return this;
		}

		public DateTime build() {
			return message;
		}
	}


	@Override public String toString() { 
		return "Timestamp [value=" + value + "]"; 
	}


}
