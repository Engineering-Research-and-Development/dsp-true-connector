package it.eng.tools.daps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DapsCacheTest {

	@InjectMocks
	private DapsCache dapsCache;
	
	@Mock
	private DapsService dapsService;
	@Mock
	private DapsProperties dapsProperties;

	@Test
	public void cacheDisabled() {
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(false);
	
		String token = dapsCache.getToken();
		
		verify(dapsService, times(0)).fetchToken();
		assertEquals(DapsCache.DUMMY_TOKEN_VALUE, token);
	}
	
	@Test
	public void cacheEnabled() throws IllegalAccessException {
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(true);
		when(dapsProperties.isTokenCaching()).thenReturn(true);

		FieldUtils.writeField(dapsCache, "cachedToken", "ABC", true);
		FieldUtils.writeField(dapsCache, "expirationTime", LocalDateTime.now().plusDays(1L), true);
		
		String token = dapsCache.getToken();
		
		verify(dapsService, times(0)).fetchToken();
		assertEquals("ABC", token);
	}
	
	@Test
	public void cacheEnabledTokenExpired() throws IllegalAccessException {
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(true);
		when(dapsProperties.isTokenCaching()).thenReturn(true);
		when(dapsService.fetchToken()).thenReturn(DapsUtils.createTestToken());

		FieldUtils.writeField(dapsCache, "cachedToken", "ABC", true);
		FieldUtils.writeField(dapsCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);
		
		String token = dapsCache.getToken();
		
		assertNotNull(token);
		verify(dapsService).fetchToken();
	}
	
	@Test
	public void cacheEnabledTokenInvalid() throws IllegalAccessException {
		when(dapsProperties.isEnabledDapsInteraction()).thenReturn(true);
		when(dapsProperties.isTokenCaching()).thenReturn(true);
		when(dapsService.fetchToken()).thenReturn("INVALID");

		FieldUtils.writeField(dapsCache, "cachedToken", "ABC", true);
		FieldUtils.writeField(dapsCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);
		
		String token = dapsCache.getToken();
		
		assertNull(token);
		verify(dapsService).fetchToken();
	}
	

}
