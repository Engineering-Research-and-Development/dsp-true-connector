package it.eng.connector.filters;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;

import it.eng.tools.model.DSpaceConstants;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonLdCompactRequestWrapper extends HttpServletRequestWrapper {

	private String body = null;

	public JsonLdCompactRequestWrapper(HttpServletRequest request) throws IOException {
		super(request);
		this.body = this.compactJsonLd(request);
	}

	private String compactJsonLd(HttpServletRequest request) throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		BufferedReader bufferedReader = null;
		try (InputStream inputStream = request.getInputStream()) {
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			char[] charBuffer = new char[128];
			int bytesRead = -1;
			while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
				stringBuilder.append(charBuffer, 0, bytesRead);
			}
		}
		String input = stringBuilder.toString();
		
		Map<String, Object> jsonObject = (Map<String, Object>) JsonUtils.fromString(input);
		Object con =  jsonObject.get(JsonLdConsts.CONTEXT);
		if(con instanceof Map) {
			log.info("Performing json-ld compact since initial context is not expected");
			jsonObject.put(JsonLdConsts.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_2024_01_VALUE);
			Map<String, Object> inContext = new HashMap<>();
			inContext.put(JsonLdConsts.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_2024_01_VALUE);
			
			JsonLdOptions ldOpts = new JsonLdOptions();
			ldOpts.setCompactArrays(Boolean.TRUE);
			ldOpts.setProcessingMode(JsonLdOptions.JSON_LD_1_1);
			
			Map<String, Object> compact = JsonLdProcessor.compact(jsonObject, inContext, ldOpts);
			String compactContent = JsonUtils.toString(compact);
			return compactContent;
		} 
		log.info("SKIPPING performing json-ld compact - context is expected");
		return input;
	}

	@Override
	public ServletInputStream getInputStream() {
		final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
		ServletInputStream servletInputStream = new ServletInputStream() {

			@Override
			public int read() {
				return byteArrayInputStream.read();
			}

			@Override
			public boolean isFinished() {
				return false;
			}

			@Override
			public boolean isReady() {
				return false;
			}

			@Override
			public void setReadListener(ReadListener listener) {

			}
		};
		return servletInputStream;
	}

	@Override
	public BufferedReader getReader() throws IOException {
		return new BufferedReader(new InputStreamReader(this.getInputStream()));
	}
}
