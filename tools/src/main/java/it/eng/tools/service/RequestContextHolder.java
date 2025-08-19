package it.eng.tools.service;

import it.eng.tools.model.RequestInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * A holder class that stores the current request information in a ThreadLocal variable.
 * This allows services to access request information without it being passed explicitly from controllers.
 */
@Slf4j
public class RequestContextHolder {

    private static final ThreadLocal<RequestInfo> requestInfoThreadLocal = new ThreadLocal<>();

    /**
     * Sets the request information for the current thread.
     *
     * @param requestInfo the request information to set
     */
    public static void setRequestInfo(RequestInfo requestInfo) {
        requestInfoThreadLocal.set(requestInfo);
    }

    /**
     * Gets the request information for the current thread.
     *
     * @return the request information, or null if not set
     */
    public static RequestInfo getRequestInfo() {
        return requestInfoThreadLocal.get();
    }

    /**
     * Clears the request information for the current thread.
     * This should be called at the end of request processing to prevent memory leaks.
     */
    public static void clearRequestInfo() {
        requestInfoThreadLocal.remove();
    }
}