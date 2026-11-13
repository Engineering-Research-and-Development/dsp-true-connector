package it.eng.negotiation.rest.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NegotiationOpenApiRequestMappingTest {

    @Test
    @DisplayName("Negotiation controllers constrain JSON consumes only on non-GET methods")
    void consumesJsonOnlyOnNonGetMethods() {
        assertEquals(0, ContractNegotiationAPIController.class
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
    }
}
