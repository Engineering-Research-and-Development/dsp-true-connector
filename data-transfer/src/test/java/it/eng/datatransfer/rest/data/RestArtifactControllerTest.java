package it.eng.datatransfer.rest.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.rest.api.RestArtifactController;
import it.eng.datatransfer.service.api.RestArtifactService;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class RestArtifactControllerTest {
	
	@Mock
	private RestArtifactService restArtifactService;
	
	@Mock
	private HttpServletResponse response;

	@InjectMocks
	private RestArtifactController restArtifactController;
	
	private static final String TRANSACTION_ID = "transactionId";
	
	@Test
	@DisplayName("Get artifact file - success")
	public void getArtifactFile_success() throws IllegalStateException, IOException  {
		doNothing().when(restArtifactService).getArtifact(TRANSACTION_ID, response);

		
		assertDoesNotThrow(() -> restArtifactController.getArtifact(response, null, TRANSACTION_ID));
		
	}
	
	@Test
	@DisplayName("Get artifact file - fail")
	public void getArtifactFile_fail() throws IllegalStateException, IOException {
		doThrow(new DownloadException("message", HttpStatus.BAD_REQUEST)).when(restArtifactService).getArtifact(TRANSACTION_ID, response);
		
		assertThrows(DownloadException.class, () -> restArtifactController.getArtifact(response, null, TRANSACTION_ID));
	}
}
