package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.model.S3UploadMode;
import org.springframework.stereotype.Component;

/**
 * Factory for creating S3 upload strategy instances based on upload mode.
 * Provides the appropriate upload strategy implementation (SYNC or ASYNC).
 */
@Component
public class S3UploadStrategyFactory {

    private final S3SyncUploadStrategy syncUploadStrategy;
    private final S3AsyncUploadStrategy asyncUploadStrategy;

    /**
     * Constructor for S3UploadStrategyFactory.
     *
     * @param syncUploadStrategy  synchronous upload strategy
     * @param asyncUploadStrategy asynchronous upload strategy
     */
    public S3UploadStrategyFactory(S3SyncUploadStrategy syncUploadStrategy,
                                   S3AsyncUploadStrategy asyncUploadStrategy) {
        this.syncUploadStrategy = syncUploadStrategy;
        this.asyncUploadStrategy = asyncUploadStrategy;
    }

    /**
     * Gets the appropriate upload strategy based on the upload mode.
     *
     * @param uploadMode the desired upload mode
     * @return the corresponding S3UploadStrategy implementation
     */
    public S3UploadStrategy getStrategy(S3UploadMode uploadMode) {
        return uploadMode == S3UploadMode.ASYNC ? asyncUploadStrategy : syncUploadStrategy;
    }
}

