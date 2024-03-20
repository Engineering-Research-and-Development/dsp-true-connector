package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LiteralExpression {

	private Object value;

	public LiteralExpression(@JsonProperty("value") Object value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "'" + value + "'";
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof LiteralExpression) {
			return ((LiteralExpression) obj).value.equals(value);
		}

		return false;
	}
}
