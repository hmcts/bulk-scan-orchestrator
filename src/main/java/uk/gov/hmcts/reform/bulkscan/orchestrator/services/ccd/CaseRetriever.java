package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.exceptions.CcdClientException;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.springframework.http.HttpStatus.NOT_FOUND;

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

    @HystrixCommand(fallbackMethod = "ccdHealthCheckFallback")
    @SuppressWarnings("unused")
    private CaseDetails retrieveFallback(
        String jurisdiction,
        String caseRef,
        CcdAuthenticator authenticator,
        Throwable exception
    ) throws Throwable {
        if (coreCaseDataApi.health().getStatus().getCode().equals(Status.UP.getCode())) {
            return retrieve(jurisdiction, caseRef, authenticator);
        }

        throw exception;
    }

    @SuppressWarnings("unused")
    private CaseDetails ccdHealthCheckFallback(
        String jurisdiction,
        String caseRef,
        CcdAuthenticator authenticator,
        Throwable exception
    ) {
        Throwable cause = exception.getCause();

        if (cause instanceof FeignException && ((FeignException) cause).status() == NOT_FOUND.value()) {
            log.info("Case not found. Ref:{}, jurisdiction:{}", caseRef, jurisdiction);

            return null;
        } else {
            throw new CcdClientException(exception.getMessage(), exception);
        }
    }
}
