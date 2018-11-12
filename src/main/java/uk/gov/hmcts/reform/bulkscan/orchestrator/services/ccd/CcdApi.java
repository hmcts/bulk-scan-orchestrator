package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;
import javax.annotation.Nonnull;

import static java.lang.String.format;

/**
 * This class is intended to be a wrapper/adaptor/facade for the orchestrator -> CcdApi.
 * In theory this should make the calls to ccd both easier to manage and quicker to refactor.
 */
@Component
public class CcdApi {
    private final CoreCaseDataApi feignCcdApi;
    private final CcdAuthenticatorFactory authenticatorFactory;

    public CcdApi(CoreCaseDataApi feignCcdApi, CcdAuthenticatorFactory authenticator) {
        this.feignCcdApi = feignCcdApi;
        this.authenticatorFactory = authenticator;
    }

    private CaseDetails retrieveCase(String caseRef, String jurisdiction) {
        CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);
        return feignCcdApi.getCase(authenticator.getUserToken(), authenticator.getServiceToken(), caseRef);
    }

    private StartEventResponse startAttachScannedDocs(String caseRef,
                                                      CcdAuthenticator authenticator,
                                                      String jurisdiction,
                                                      String caseTypeId) {
        return feignCcdApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseRef,
            "attachScannedDocs"
        );
    }

    @Nonnull
    StartEventResponse startAttachScannedDocs(CaseDetails theCase) {
        String caseRef = String.valueOf(theCase.getId());
        try {
            CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(theCase.getJurisdiction());
            return startAttachScannedDocs(caseRef, authenticator, theCase.getJurisdiction(), theCase.getCaseTypeId());
        } catch (FeignException e) {
            throw error(e, "Internal Error: start event call failed case: %s Error: %s", caseRef, e.status());
        }
    }

    @Nonnull
    @SuppressWarnings("squid:S1135")
    CaseDetails getCase(String caseRef, String jurisdiction) {
        try {
            //TODO: RPE-823 merge with `CaseRetriever` to a consistent api adaptor
            return retrieveCase(caseRef, jurisdiction);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw error(e, "Could not find case: %s", caseRef);
            } else {
                throw error(e, "Internal Error: Could not retrieve case: %s Error: %s", caseRef, e.status());
            }
        }
    }

    void attachExceptionRecord(CaseDetails theCase,
                               Map<String, Object> data,
                               String eventSummary,
                               StartEventResponse event) {
        String caseRef = String.valueOf(theCase.getId());
        String jurisdiction = theCase.getJurisdiction();
        String caseTypeId = theCase.getCaseTypeId();
        try {
            CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);
            attachCall(caseRef,
                authenticator,
                data,
                event.getToken(),
                jurisdiction,
                caseTypeId,
                Event.builder().summary(eventSummary).id(event.getEventId()).build());
        } catch (FeignException e) {
            throw error(e, "Internal Error: submitting attach file event failed case: %s Error: %s",
                caseRef, e.status());
        }
    }

    private void attachCall(String caseRef,
                            CcdAuthenticator authenticator,
                            Map<String, Object> data,
                            String eventToken,
                            String jurisdiction,
                            String caseTypeId,
                            Event eventInfo) {
        feignCcdApi.submitEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseRef,
            true,
            CaseDataContent.builder()
                .data(data)
                .event(eventInfo)
                .eventToken(eventToken)
                .build()
        );
    }

    private static CallbackException error(Exception e, String errorFmt, Object arg) {
        return error(e, errorFmt, arg, null);
    }

    private static CallbackException error(Exception e, String errorFmt, Object arg1, Object arg2) {
        return new CallbackException(format(errorFmt, arg1, arg2), e);
    }

}
