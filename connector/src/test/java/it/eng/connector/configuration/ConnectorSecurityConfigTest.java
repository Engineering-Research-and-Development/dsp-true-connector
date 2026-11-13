package it.eng.connector.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectorSecurityConfigTest {

    @ParameterizedTest(name = "remote {0}, local {1} -> {2}")
    @CsvSource({
            "127.0.0.1, 127.0.0.1, true",
            "::1, ::1, true",
            "0:0:0:0:0:0:0:1, 127.0.0.1, true",
            "127.0.0.1, 10.1.2.3, false",
            "127.0.0.1, 0.0.0.0, false",
            "10.1.2.3, 127.0.0.1, false",
            "10.1.2.3, 10.1.2.4, false"
    })
    @DisplayName("A loopback connection needs both the remote and the local address to be loopback")
    void loopbackConnectionNeedsBothEnds(String remoteAddress, String localAddress, boolean expected) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.setLocalAddr(localAddress);

        assertEquals(expected, ConnectorSecurityConfig.isLoopbackConnection(request));
    }
}
