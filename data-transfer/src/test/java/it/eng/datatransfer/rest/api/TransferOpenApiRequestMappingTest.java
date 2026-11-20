package it.eng.datatransfer.rest.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.ftp.rest.api.FTPClientAPIController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class TransferOpenApiRequestMappingTest {

    @Test
    @DisplayName("FTP API scopes JSON consumes to its POST method")
    void ftpConsumesJsonOnlyOnPostMethod() throws NoSuchMethodException {
        assertEquals(0, FTPClientAPIController.class
                .getAnnotation(RequestMapping.class).consumes().length);

        Method downloadArtifact = FTPClientAPIController.class.getDeclaredMethod("downloadArtifact", JsonNode.class);
        assertArrayEquals(new String[]{MediaType.APPLICATION_JSON_VALUE},
                downloadArtifact.getAnnotation(PostMapping.class).consumes());
    }

    @Test
    @DisplayName("Transfer API GET methods have no consumes restriction")
    void getMethodsHaveNoConsumesRestriction() {
        for (Method method : DataTransferAPIController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)) {
                assertEquals(0, method.getAnnotation(GetMapping.class).consumes().length, method.getName());
            }
        }
    }
}
