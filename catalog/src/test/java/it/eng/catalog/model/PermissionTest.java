package it.eng.catalog.model;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class PermissionTest {

    private static final String TARGET_A = "urn:uuid:TARGET";

    private final Constraint constraint = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.DATE_TIME)
            .operator(Operator.GT)
            .rightOperand("2024-02-29T00:00:01+01:00")
            .build();

    private final Permission permissionA = Permission.Builder.newInstance()
            .action(Action.USE)
            .target(TARGET_A)
            .constraint(Set.of(constraint))
            .build();

    private final Permission permissionB = Permission.Builder.newInstance()
            .action(Action.ANONYMIZE)
            .target(TARGET_A)
            .constraint(Set.of(constraint))
            .build();

    @Test
    public void equalsTrue() {
        assertTrue(permissionA.equals(permissionA));
    }

    @Test
    public void equalsFalse() {
        assertFalse(permissionA.equals(permissionB));
    }

    @Test
    public void equalsFalse_array() {
        Constraint constraint2 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.DATE_TIME)
                .operator(Operator.EQ)
                .rightOperand("2024-02-29T00:00:01+01:00")
                .build();
        Permission permissionArray = Permission.Builder.newInstance()
                .action(Action.ANONYMIZE)
                .target(TARGET_A)
                .constraint(Set.of(constraint2, constraint))
                .build();
        assertFalse(permissionA.equals(permissionArray));
    }

    @Test
    public void equalsTrue_array() {
        Constraint constraint1 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.DATE_TIME)
                .operator(Operator.GT)
                .rightOperand("2024-02-29T00:00:01+01:00")
                .build();
        Constraint constraint2 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.COUNT)
                .operator(Operator.EQ)
                .rightOperand("5")
                .build();

        Permission permissionA = Permission.Builder.newInstance()
                .action(Action.USE)
                .target(TARGET_A)
                .constraint(Set.of(constraint1, constraint2))
                .build();
        Permission permissionB = Permission.Builder.newInstance()
                .action(Action.USE)
                .target(TARGET_A)
                .constraint(Set.of(constraint2, constraint1))
                .build();

        assertTrue(permissionA.equals(permissionB));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        Permission permission = CatalogMockObjectUtil.PERMISSION;
        String ss = CatalogSerializer.serializePlain(permission);
        Permission permission2 = CatalogSerializer.deserializePlain(ss, Permission.class);
        assertThat(permission).usingRecursiveComparison().isEqualTo(permission2);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        Permission permission = CatalogMockObjectUtil.PERMISSION;
        String ss = CatalogSerializer.serializeProtocol(permission);
        Permission permission2 = CatalogSerializer.deserializeProtocol(ss, Permission.class);
        assertThat(permission).usingRecursiveComparison().isEqualTo(permission2);
    }

    @Test
    @DisplayName("Validate protocol - valid permission")
    public void validateProtocolValid() {
        Permission permission = CatalogMockObjectUtil.PERMISSION;
        // This should not throw an exception
        permission.validateProtocol();
    }

    @Test
    @DisplayName("Validate - missing action")
    public void validateMissingAction() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> Permission.Builder.newInstance()
                        .constraint(Set.of(constraint))
                        .build());
        assertEquals("Permission - action must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Validate - missing constraint")
    public void validateMissingConstraint() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> Permission.Builder.newInstance()
                        .action(Action.USE)
                        .build());
        assertEquals("Permission - constraint must not be null", exception.getMessage());
    }
}
