package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Getter
@EqualsAndHashCode   // requires for offer check in negotiation flow
@JsonDeserialize(builder = Constraint.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LeftOperand leftOperand;

    private Operator operator;

    private String rightOperand;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final Constraint constraint;

        private Builder() {
            constraint = new Constraint();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder leftOperand(LeftOperand leftOperand) {
            constraint.leftOperand = leftOperand;
            return this;
        }

        public Builder operator(Operator operator) {
            constraint.operator = operator;
            return this;
        }

        public Builder rightOperand(String rightOperand) {
            constraint.rightOperand = rightOperand;
            return this;
        }

        public Constraint build() {
            return constraint;
        }
    }
}
