package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.model.S3UploadMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class S3UploadStrategyFactoryTest {

    @Mock
    private S3SyncUploadStrategy syncUploadStrategy;

    @Mock
    private S3AsyncUploadStrategy asyncUploadStrategy;

    private S3UploadStrategyFactory factory;

    @BeforeEach
    void setUp() {
        factory = new S3UploadStrategyFactory(syncUploadStrategy, asyncUploadStrategy);
    }

    @Test
    @DisplayName("Should return async strategy when ASYNC mode is requested")
    void getStrategy_AsyncMode() {
        // Act
        S3UploadStrategy result = factory.getStrategy(S3UploadMode.ASYNC);

        // Assert
        assertSame(asyncUploadStrategy, result);
    }

    @Test
    @DisplayName("Should return sync strategy when SYNC mode is requested")
    void getStrategy_SyncMode() {
        // Act
        S3UploadStrategy result = factory.getStrategy(S3UploadMode.SYNC);

        // Assert
        assertSame(syncUploadStrategy, result);
    }
}

