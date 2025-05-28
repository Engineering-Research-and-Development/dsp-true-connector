package it.eng.datatransfer.service.api;

import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.service.api.strategy.HttpPullTransferStrategy;
import it.eng.datatransfer.service.api.strategy.HttpPushTransferStrategy;
import it.eng.datatransfer.service.api.strategy.S3TransferStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class DataTransferStrategyFactory {

    private final Map<DataTransferFormat, DataTransferStrategy> strategies;

    public DataTransferStrategyFactory(
            HttpPullTransferStrategy httpPullStrategy,
            HttpPushTransferStrategy httpPushStrategy,
            S3TransferStrategy s3Strategy) {
        strategies = Map.of(
                DataTransferFormat.HTTP_PULL, httpPullStrategy
                //DataTransferFormat.HTTP_PUSH, httpPushStrategy;
                //DataTransferFormat.S3, s3Strategy
        );
    }

    public DataTransferStrategy getStrategy(String endpointType) {
        log.debug("Getting strategy for endpointType {}", endpointType);
        return strategies.get(DataTransferFormat.fromString(endpointType));
    }

}
