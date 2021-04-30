package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;

import java.util.List;

@Service
public class CallbackResultService {
    private static final Logger log = LoggerFactory.getLogger(CallbackResultService.class);

    private final CallbackResultRepository callbackResultRepository;

    public CallbackResultService(CallbackResultRepository callbackResultRepository) {
        this.callbackResultRepository = callbackResultRepository;
    }

    public List<CallbackResult> findByExceptionRecordId(String exceptionRecordId) {
        log.info("Fetching callback results for exceptionRecordId {}", exceptionRecordId);

        return callbackResultRepository.findByExceptionRecordId(exceptionRecordId);
    }

    public List<CallbackResult> findByCaseId(String caseId) {
        log.info("Fetching callback results for exceptionRecordId {}", caseId);

        return callbackResultRepository.findByCaseId(caseId);
    }
}
