package it.eng.catalog.model;

import it.eng.catalog.serializer.CatalogSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstraintTest {

    private final Constraint constraintA = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.DATE_TIME)
            .operator(Operator.GT)
            .rightOperand("2024-02-29T00:00:01+01:00")
            .build();

    private final Constraint constraintB = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.DATE_TIME)
            .operator(Operator.EQ)
            .rightOperand("2024-02-29T00:00:01+01:00")
            .build();

    @Test
    public void equalsTrue() {
        assertTrue(constraintA.equals(constraintA));
    }

    @Test
    public void equalsFalse() {
        assertFalse(constraintA.equals(constraintB));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = CatalogSerializer.serializePlain(constraintA);
        Constraint constraintA2 = CatalogSerializer.deserializePlain(ss, Constraint.class);
        assertThat(constraintA).usingRecursiveComparison().isEqualTo(constraintA2);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = CatalogSerializer.serializeProtocol(constraintA);
        Constraint constraintA2 = CatalogSerializer.deserializeProtocol(ss, Constraint.class);
        assertThat(constraintA).usingRecursiveComparison().isEqualTo(constraintA2);
    }
}
