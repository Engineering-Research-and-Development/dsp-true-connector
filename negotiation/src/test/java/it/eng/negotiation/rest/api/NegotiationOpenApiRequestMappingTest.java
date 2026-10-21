package it.eng.negotiation.rest.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class NegotiationOpenApiRequestMappingTest {

    @Test
    @DisplayName("Negotiation controllers constrain JSON consumes only on non-GET methods")
    void consumesJsonOnlyOnNonGetMethods() {
        assertEquals(0, ContractNegotiationAPIController.class
                .getAnnotation(RequestMapping.class).consumes().length);
        assertEquals(0, AgreementAPIController.class
                .getAnnotation(RequestMapping.class).consumes().length);

        for (Method method : ContractNegotiationAPIController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)) {
                assertEquals(0, method.getAnnotation(GetMapping.class).consumes().length, method.getName());
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                assertArrayEquals(new String[]{MediaType.APPLICATION_JSON_VALUE},
                        method.getAnnotation(PostMapping.class).consumes(), method.getName());
            } else if (method.isAnnotationPresent(PutMapping.class)) {
                assertArrayEquals(new String[]{MediaType.APPLICATION_JSON_VALUE},
                        method.getAnnotation(PutMapping.class).consumes(), method.getName());
            }
        }

        Method enforceAgreement = agreementMethod();
        assertArrayEquals(new String[]{MediaType.APPLICATION_JSON_VALUE},
                enforceAgreement.getAnnotation(PostMapping.class).consumes());
    }

    private static Method agreementMethod() {
        try {
            return AgreementAPIController.class.getDeclaredMethod("enforceAgreement", String.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
