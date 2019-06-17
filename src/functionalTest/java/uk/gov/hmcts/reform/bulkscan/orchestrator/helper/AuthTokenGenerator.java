package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;

public class AuthTokenGenerator {

    private final CcdAuthenticatorFactory ccdAuthenticatorFactory;

    public AuthTokenGenerator(CcdAuthenticatorFactory ccdAuthenticatorFactory) {
        this.ccdAuthenticatorFactory = ccdAuthenticatorFactory;
    }

    public CcdAuthenticator getAuthTokenFor(String jurisdiction) {
        return ccdAuthenticatorFactory.createForJurisdiction(jurisdiction);
    }
}
