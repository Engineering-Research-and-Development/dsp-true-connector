package it.eng.datatransfer.rest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.rest.api.RestArtifactController;
import it.eng.datatransfer.service.api.RestArtifactService;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.Response;
import okhttp3.ResponseBody;

@ExtendWith(MockitoExtension.class)
public class RestArtifactControllerTest {
	
	private InputStream inputStream = new ByteArrayInputStream(DATA.getBytes());
	
	@Mock
	Response response;
	
	@Mock
	ResponseBody responseBody;
		
	@Mock
	private GridFsResource gridFsResource;
	
	@Mock
	private RestArtifactService restArtifactService;

	@InjectMocks
	private RestArtifactController restArtifactController;
	
	private static final String TRANSACTION_ID = "transactionId";
	private static final String DATA = "data";
	
	@Test
	@DisplayName("Get artifact file - success")
	public void getArtifactFile_success() throws IllegalStateException, IOException  {
		MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
		when(restArtifactService.getArtifact(TRANSACTION_ID)).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE);
		when(restArtifactService.streamAttachment(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getValue())).thenReturn(gridFsResource);
		when(gridFsResource.getInputStream()).thenReturn(inputStream);
		when(gridFsResource.getFilename()).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getFilename());
		when(gridFsResource.getContentType()).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getContentType());
		
		restArtifactController.getArtifact(httpServletResponse, null, TRANSACTION_ID);
		
		assertEquals(httpServletResponse.getContentType(), it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getContentType());
		assertEquals(httpServletResponse.getContentAsString(), DATA);
		assertTrue(httpServletResponse.getHeader(HttpHeaders.CONTENT_DISPOSITION).contains(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getFilename()));
		assertEquals(httpServletResponse.getStatus(), HttpStatus.OK.value());
	}
	
	@Test
	@DisplayName("Get artifact file - fail")
	public void getArtifactFile_fail() throws IllegalStateException, IOException {
		MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
		when(restArtifactService.getArtifact(TRANSACTION_ID)).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE);
		when(restArtifactService.streamAttachment(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getValue())).thenReturn(gridFsResource);
		when(gridFsResource.getInputStream()).thenThrow(IOException.class);
		
		assertThrows(DownloadException.class, () -> restArtifactController.getArtifact(httpServletResponse, null, TRANSACTION_ID));
	}
	
	@Test
	@DisplayName("Get artifact external - success")
	public void getArtifactExternal_success() throws IllegalStateException, IOException  {
		MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
		when(restArtifactService.getArtifact(TRANSACTION_ID)).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL);
		when(restArtifactService.getExternalData(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL.getValue())).thenReturn(response);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.byteStream()).thenReturn(inputStream);
		when(response.header(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.APPLICATION_JSON_VALUE);

		
		restArtifactController.getArtifact(httpServletResponse, null, TRANSACTION_ID);
		
		assertEquals(httpServletResponse.getContentType(), MediaType.APPLICATION_JSON_VALUE);
		assertEquals(httpServletResponse.getContentAsString(), DATA);
		assertEquals(httpServletResponse.getStatus(), HttpStatus.OK.value());
	}
	
	@Test
	@DisplayName("Get artifact external - fail")
	public void getArtifactExternal_fail() throws IllegalStateException, IOException {
		HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
		when(restArtifactService.getArtifact(TRANSACTION_ID)).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL);
		when(restArtifactService.getExternalData(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL.getValue())).thenReturn(response);
		when(response.body()).thenReturn(responseBody);
		when(responseBody.byteStream()).thenReturn(inputStream);
		when(httpServletResponse.getOutputStream()).thenThrow(IOException.class);
		
		assertThrows(DownloadException.class, () -> restArtifactController.getArtifact(httpServletResponse, null, TRANSACTION_ID));
	}
	
	@Test
	@DisplayName("Get artifact - wrong artifact type")
	public void getArtifact_wrongArtifactType() throws IllegalStateException, IOException {
		HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
		when(restArtifactService.getArtifact(TRANSACTION_ID)).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL);
		
		assertThrows(DownloadException.class, () -> restArtifactController.getArtifact(httpServletResponse, null, TRANSACTION_ID));
	}
}
