package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult;

@Component
public class CallbackResultRepositoryProxy {
    private static final Logger log = LoggerFactory.getLogger(CallbackResultRepositoryProxy.class);

    private final CallbackResultRepository callbackResultRepository;
    private final int retryCount;

    public CallbackResultRepositoryProxy(
        CallbackResultRepository callbackResultRepository,
        @Value("${callback.store.retry-count}") final int retryCount
    ) {
        this.callbackResultRepository = callbackResultRepository;
        this.retryCount = retryCount;
    }

    public void storeCallbackResult(NewCallbackResult callbackResult) {
        for (int i = 0; i < retryCount; i++) {
            try {
                callbackResultRepository.insert(callbackResult);

                log.info(
                    "Successfully stored callback result {}, "
                        + "exception record Id {}, case Id {}, retry {}",
                    callbackResult.requestType,
                    callbackResult.exceptionRecordId,
                    callbackResult.caseId,
                    i
                );

                return;
            } catch (Exception ex) {
                log.error(
                    "Failed to store callback result {}, "
                        + "exception record Id {}, case Id {}, retry {}",
                    callbackResult.requestType,
                    callbackResult.exceptionRecordId,
                    callbackResult.caseId,
                    i,
                    ex
                );
            }
        }
        log.error(
            "Failed to store callback result {}, "
                + "exception record Id {}, case Id {} after {} retries",
            callbackResult.requestType,
            callbackResult.exceptionRecordId,
            callbackResult.caseId,
            retryCount
        );
    }
}
