package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import javax.annotation.Nonnull;

import static java.lang.String.format;

@Component
public class CallbackCcdApi {
    private final CoreCaseDataApi ccdApi;
    private final CcdAuthenticatorFactory authenticatorFactory;

    public CallbackCcdApi(CoreCaseDataApi ccdApi, CcdAuthenticatorFactory authenticator) {
        this.ccdApi = ccdApi;
        this.authenticatorFactory = authenticator;
    }

    private CaseDetails retrieveCase(String caseRef, String jurisdiction) {
        CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);
        return ccdApi.getCase(authenticator.getUserToken(), authenticator.getServiceToken(), caseRef);
    }

    private StartEventResponse startAttachScannedDocs(String caseRef,
                                                      CcdAuthenticator authenticator,
                                                      String jurisdiction,
                                                      String caseTypeId) {
        return ccdApi.startEventForCaseWorker(
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
    CaseDetails getCase(String caseRef, String jurisdiction) {
        try {
            return retrieveCase(caseRef, jurisdiction);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw error(e, "Could not find case: %s", caseRef);
            } else {
                throw error(e, "Internal Error: Could not retrieve case: %s Error: %s", caseRef, e.status());
            }
        }
    }

    private static CallbackException error(Exception e, String errorFmt, Object arg) {
        return error(e, errorFmt, arg, null);
    }

    private static CallbackException error(Exception e, String errorFmt, Object arg1, Object arg2) {
        return new CallbackException(format(errorFmt, arg1, arg2), e);
    }

}
