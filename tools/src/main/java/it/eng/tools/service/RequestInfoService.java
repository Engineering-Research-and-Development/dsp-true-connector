package it.eng.tools.service;

import it.eng.tools.model.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
@Slf4j
public class RequestInfoService {

    private final String UNKNOWN = "unknown";
    private final String LOCALHOST_IPV4 = "127.0.0.1";
    private final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * Gets request information from the current request.
     * This method can be called from anywhere in the application, including service classes.
     *
     * @return the request information, or null if not available
     */
    public RequestInfo getCurrentRequestInfo() {
        return RequestContextHolder.getRequestInfo();
    }

    /**
     * Gets request information from the provided HttpServletRequest.
     * This method is primarily used by the RequestContextFilter.
     *
     * @param request the HTTP servlet request
     * @return the request information
     */
    public RequestInfo getRequestInfo(HttpServletRequest request) {
        return RequestInfo.Builder.newInstance()
                .method(request.getMethod())
                .remoteAddress(getClientIp(request))
                .remoteHost(request.getRemoteHost())
                .username(request.getRemoteUser())
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (StringUtils.isEmpty(ipAddress) || UNKNOWN.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }

        if (StringUtils.isEmpty(ipAddress) || UNKNOWN.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }

        if (StringUtils.isEmpty(ipAddress) || UNKNOWN.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
            if (LOCALHOST_IPV4.equals(ipAddress) || LOCALHOST_IPV6.equals(ipAddress)) {
                try {
                    InetAddress inetAddress = InetAddress.getLocalHost();
                    ipAddress = inetAddress.getHostAddress();
                } catch (UnknownHostException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }

        if (!StringUtils.isEmpty(ipAddress)
                && ipAddress.length() > 15
                && ipAddress.indexOf(",") > 0) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
        }

        return ipAddress;
    }
}
