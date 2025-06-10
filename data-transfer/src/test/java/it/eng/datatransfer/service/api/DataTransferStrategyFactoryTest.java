package it.eng.datatransfer.service.api;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.service.api.strategy.HttpPullTransferStrategy;
import it.eng.datatransfer.service.api.strategy.HttpPushTransferStrategy;
import it.eng.datatransfer.service.api.strategy.S3TransferStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class DataTransferStrategyFactoryTest {

    private HttpPullTransferStrategy httpPullStrategy;
    private HttpPushTransferStrategy httpPushStrategy;
    private S3TransferStrategy s3Strategy;
    private DataTransferStrategyFactory factory;

    @BeforeEach
    void setUp() {
        httpPullStrategy = mock(HttpPullTransferStrategy.class);
        httpPushStrategy = mock(HttpPushTransferStrategy.class);
        s3Strategy = mock(S3TransferStrategy.class);
        factory = new DataTransferStrategyFactory(httpPullStrategy, httpPushStrategy, s3Strategy);
    }

    @Test
    @DisplayName("Should return HTTP_PULL strategy for supported format")
    void getStrategy_HttpPull_Success() {
        var strategy = factory.getStrategy(DataTransferFormat.HTTP_PULL.format());
        assertSame(httpPullStrategy, strategy);
    }

    @Test
    @DisplayName("Should throw exception for supported format not in map (HTTP_PUSH)")
    void getStrategy_HttpPush_NotInMap() {
        DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                () -> factory.getStrategy("HttpData-PUSH"));
        assertTrue(ex.getMessage().contains("Invalid endpoint type:"));
    }

    @Test
    @DisplayName("Should throw exception for unsupported format (S3)")
    void getStrategy_S3_NotInMap() {
        DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                () -> factory.getStrategy("S3"));
        assertTrue(ex.getMessage().contains("Invalid endpoint type:"));
    }

    @Test
    @DisplayName("Should throw exception for invalid format string")
    void getStrategy_InvalidFormat() {
        DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                () -> factory.getStrategy("INVALID_FORMAT"));
        assertTrue(ex.getMessage().contains("Invalid endpoint type"));
    }

    @Test
    @DisplayName("Should throw exception for null input (edge case)")
    void getStrategy_NullInput() {
        DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                () -> factory.getStrategy(null));
        assertTrue(ex.getMessage().contains("Invalid endpoint type"));
    }
}
