package it.eng.dataplane.s3.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DataPlaneS3StartupBean}.
 */
class DataPlaneS3StartupBeanTest {

    @Test
    @DisplayName("startup bean keeps only no-arg placeholder wiring and no instance dependencies")
    void startupBean_keepsOnlyPlaceholderWiringWithoutDependencies() throws NoSuchMethodException {
        Constructor<?>[] constructors = DataPlaneS3StartupBean.class.getDeclaredConstructors();
        List<Field> instanceFields = Arrays.stream(DataPlaneS3StartupBean.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        Method ensureBucketCredentials = DataPlaneS3StartupBean.class.getDeclaredMethod("ensureBucketCredentials");

        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
        assertTrue(instanceFields.isEmpty(), "Placeholder bean should not keep provisioning/config dependencies");
        assertNotNull(ensureBucketCredentials.getAnnotation(EventListener.class));
        assertDoesNotThrow(() -> new DataPlaneS3StartupBean().ensureBucketCredentials());
    }
}
