package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CaseRetriever {
    public static final String CASE_TYPE_ID = "Bulk_Scanned";

    private static final Logger log = LoggerFactory.getLogger(CaseRetriever.class);

    private final CcdAuthenticatorFactory factory;

    @LoadBalanced
    private final CoreCaseDataApi coreCaseDataApi;

    CaseRetriever(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public CcdAuthenticator authenticate(String jurisdiction) {
        return factory.createForJurisdiction(jurisdiction);
    }

    @HystrixCommand(
        commandKey = "case-retrieval",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "5000")
        },
        fallbackMethod = "retrieveFallback"
    )
    public CaseDetails retrieve(String jurisdiction, String caseRef, CcdAuthenticator authenticator) {
        CaseDetails caseDetails = coreCaseDataApi.readForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.userDetails.getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef
        );

        if (caseDetails != null) {
            log.info(
                "Found worker case: {}:{}:{}",
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId()
            );
        }

        return caseDetails;
    }

    @SuppressWarnings("unused")
    private CaseDetails retrieveFallback(
        String jurisdiction,
        String caseRef,
        CcdAuthenticator authenticator,
        Throwable exception
    ) {
        String message = exception.getMessage();

        if (exception instanceof FeignException) {
            HttpStatus status = HttpStatus.valueOf(((FeignException) exception).status());

            logResponse(jurisdiction, caseRef, status, message);
        } else {
            log.error(
                "Could not retrieve case with ref:{} for {} jurisdiction. Error: {}",
                caseRef,
                jurisdiction,
                message,
                exception
            );
        }

        return null;
    }

    private void logResponse(String jurisdiction, String caseRef, HttpStatus status, String originalMessage) {
        if (HttpStatus.NOT_FOUND.equals(status)) {
            log.info(
                "Not found case ref:{} for {} jurisdiction {}. Error: {}",
                caseRef,
                jurisdiction,
                originalMessage
            );
        } else if (status.is4xxClientError()) {
            log.warn(
                "Client error response {} when searching case ref:{} for {} jurisdiction. Error: {}",
                status.value(),
                caseRef,
                jurisdiction,
                originalMessage
            );
        } else if (status.is5xxServerError()) {
            log.error(
                "Server error response {} when searching case ref:{} for {} jurisdiction. Error: {}",
                status.value(),
                caseRef,
                jurisdiction,
                originalMessage
            );
        }
    }
}
