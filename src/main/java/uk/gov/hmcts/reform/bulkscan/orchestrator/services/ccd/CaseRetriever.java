package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CaseRetriever {

    private static final Logger log = LoggerFactory.getLogger(CaseRetriever.class);

    private final CcdAuthenticatorFactory factory;

    private final CoreCaseDataApi coreCaseDataApi;

    public CaseRetriever(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public CaseDetails retrieve(String jurisdiction, CaseTypeId caseTypeId, String caseRef) {
        // not including in try catch to fast fail the method
        CcdAuthenticator authenticator = factory.createForJurisdiction(jurisdiction);

        try {
            CaseDetails caseDetails = coreCaseDataApi.readForCaseWorker(
                authenticator.getUserToken(),
                authenticator.getServiceToken(),
                authenticator.getUserDetails().getId(),
                jurisdiction,
                caseTypeId.getId(),
                caseRef
            );

            logCaseDetails(caseDetails);

            return caseDetails;
        } catch (FeignException exception) {
            if (exception.status() == NOT_FOUND.value()) {
                log.info("Case not found. Ref:{}, jurisdiction:{}", caseRef, jurisdiction);

                return null;
            } else {
                throw exception;
            }
        }
    }

    private void logCaseDetails(CaseDetails caseDetails) {
        if (caseDetails != null) {
            log.info(
                "Found worker case: {}:{}:{}",
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId()
            );
        }
    }
}
