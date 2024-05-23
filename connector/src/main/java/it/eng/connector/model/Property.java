package it.eng.connector.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;

@Getter
@JsonDeserialize(builder = Property.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {DSpaceConstants.ID, DSpaceConstants.KEY, DSpaceConstants.VALUE, DSpaceConstants.SAMPLE_VALUE, DSpaceConstants.MANDATORY}
, alphabetic = true)
@Document(collection = "properties")
public class Property {

	@JsonProperty(DSpaceConstants.ID)
	@Setter
	@Id
	private String id;

	@JsonProperty(DSpaceConstants.KEY)
	private String key;

	@JsonProperty(DSpaceConstants.VALUE)
	private String value;

	@JsonProperty(DSpaceConstants.SAMPLE_VALUE)
	private String sampleValue;

	@JsonProperty(DSpaceConstants.MANDATORY)
	private boolean mandatory;

	@JsonProperty(DSpaceConstants.INSERT_DATE)
	@Setter
	private String insertDate;

	@JsonProperty(DSpaceConstants.LAST_CHANGE_DATE)
	private String lastChangeDate;

	public static class Builder {
		private Property property;

		private Builder() {
			property = new  Property();
		}

		public static Builder newInstance() {
			return new Builder();
		}

		@JsonProperty(DSpaceConstants.ID)
		public Builder id(String id) {
			property.id = id;
			return this;
		}

		@JsonProperty(DSpaceConstants.KEY)
		public Builder key(String key) {
			property.key = key;
			return this;
		}

		@JsonProperty(DSpaceConstants.VALUE)
		public Builder value(String value) {
			property.value = value;
			return this;
		}

		@JsonProperty(DSpaceConstants.SAMPLE_VALUE)
		public Builder defaultValue(String defaultValue) {
			property.sampleValue = defaultValue;
			return this;
		}

		@JsonProperty(DSpaceConstants.MANDATORY)
		public Builder mandatory(boolean mandatory) {
			property.mandatory = mandatory;
			return this;
		}

		public Property build() {
			if(property.id == null && property.key != null && !property.key.isEmpty()) {
				property.id = property.key;
			}
			
			OffsetDateTime now = OffsetDateTime.now( ZoneOffset.UTC );
			property.insertDate = now.toString();
			property.lastChangeDate = now.toString();

			Set<ConstraintViolation<Property>> violations
			= Validation.buildDefaultValidatorFactory().getValidator().validate(property);
			if(violations.isEmpty()) {
				return property;
			}
			throw new ValidationException("Property - " +
					violations
					.stream()
			.map(v -> v.getPropertyPath() + " " + v.getMessage())
			.collect(Collectors.joining(",")));
		}
	}

	/*
	 * public Property(String key, String value) { this.id = key; this.key = key;
	 * this.value = value; OffsetDateTime now = OffsetDateTime.now( ZoneOffset.UTC
	 * ); this.insertDate = now.toString(); this.lastChangeDate = now.toString(); }
	 */

	@Override
	public int hashCode() {
		return Objects.hash(mandatory, key, sampleValue, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass()) {
			return false;
		}
		Property other = (Property) obj;
		return mandatory == other.mandatory && Objects.equals(key, other.key) 
				&& Objects.equals(sampleValue, other.sampleValue) && Objects.equals(value, other.value);
	}

	@Override
	public String toString() {
		return "Property [" + DSpaceConstants.ID + "=" + id + 
				", " + DSpaceConstants.KEY + "=" + key + 
				", " + DSpaceConstants.VALUE + "=" + value + 
				", " + DSpaceConstants.SAMPLE_VALUE + "=" + sampleValue +
				", " + DSpaceConstants.MANDATORY + "=" + mandatory + 
				", " + DSpaceConstants.INSERT_DATE + "=" + insertDate +
				", " + DSpaceConstants.LAST_CHANGE_DATE + "=" + lastChangeDate + "]";
	}


}
