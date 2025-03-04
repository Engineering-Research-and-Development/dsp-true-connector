package it.eng.tools.exception;

import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExceptionAPIAdviceTest {

    @InjectMocks
    private ExceptionAPIAdvice exceptionAPIAdvice;

    @Mock
    private WebRequest webRequest;

    @Test
    @DisplayName("Handle BadRequestException")
    void handleBadRequestException_returnsCorrectResponse() throws Exception {
        // Arrange
        String errorMessage = "Bad request error";
        BadRequestException exception = new BadRequestException(errorMessage);

        // Act
        ResponseEntity<Object> responseEntity = exceptionAPIAdvice.handleBadRequestExceptionAPIException(exception, webRequest);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody() instanceof GenericApiResponse);
        GenericApiResponse<?> response = (GenericApiResponse<?>) responseEntity.getBody();
        
        // Use reflection to access fields directly
        Field messageField = GenericApiResponse.class.getDeclaredField("message");
        messageField.setAccessible(true);
        assertEquals(errorMessage, messageField.get(response));
        
        Field successField = GenericApiResponse.class.getDeclaredField("success");
        successField.setAccessible(true);
        assertFalse((Boolean) successField.get(response));
        
        Field timestampField = GenericApiResponse.class.getDeclaredField("timestamp");
        timestampField.setAccessible(true);
        assertNotNull(timestampField.get(response));
    }

    @Test
    @DisplayName("Handle ResourceNotFoundException")
    void handleResourceNotFoundException_returnsCorrectResponse() throws Exception {
        // Arrange
        String errorMessage = "Resource not found error";
        ResourceNotFoundException exception = new ResourceNotFoundException(errorMessage);

        // Act
        ResponseEntity<Object> responseEntity = exceptionAPIAdvice.handleResourceNotFoundExceptionAPIException(exception, webRequest);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody() instanceof GenericApiResponse);
        GenericApiResponse<?> response = (GenericApiResponse<?>) responseEntity.getBody();
        
        // Use reflection to access fields directly
        Field messageField = GenericApiResponse.class.getDeclaredField("message");
        messageField.setAccessible(true);
        assertEquals(errorMessage, messageField.get(response));
        
        Field successField = GenericApiResponse.class.getDeclaredField("success");
        successField.setAccessible(true);
        assertFalse((Boolean) successField.get(response));
        
        Field timestampField = GenericApiResponse.class.getDeclaredField("timestamp");
        timestampField.setAccessible(true);
        assertNotNull(timestampField.get(response));
    }
}
