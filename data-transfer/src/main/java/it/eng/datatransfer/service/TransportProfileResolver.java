package it.eng.datatransfer.service;

import it.eng.datatransfer.model.TransportProfile;
import org.springframework.stereotype.Component;

/**
 * Resolves the internal transport profile for a given transfer type.
 *
 * <p>The transport profile is an internal routing hint used by the Control Plane to select
 * the appropriate Data Plane instance. It is not exposed in DSP protocol messages.</p>
 *
 * <p>Returns {@code null} when no specialized transport is required (e.g. standard HTTP-PULL/PUSH).</p>
 */
@Component
public class TransportProfileResolver {

    /**
     * Resolves the transport profile for the given transfer type.
     *
     * <p>Returns {@link TransportProfile#STREAM_GRPC} when the transfer type is {@code stream:grpc}.
     * Returns {@code null} for all other transfer types, including HTTP-PULL and HTTP-PUSH.</p>
     *
     * @param transferType the transfer type identifier (e.g. {@code "stream:grpc"}, {@code "HttpData-PULL"})
     * @return the matching transport profile string, or {@code null} if no profile applies
     */
    public String resolve(String transferType) {
        if (TransportProfile.STREAM_GRPC.equals(transferType)) {
            return TransportProfile.STREAM_GRPC;
        }
        return null;
    }
}
