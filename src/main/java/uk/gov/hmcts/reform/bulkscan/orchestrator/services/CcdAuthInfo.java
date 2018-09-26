package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

public class CcdAuthInfo {
    final UserDetails userDetails;
    final String sscsToken;
    private final Credential user;
    final String userToken;
    final String jursdiction;

    public CcdAuthInfo(String sscsToken, Credential user, String userToken, UserDetails userDetails, String jurisdiction) {
        this.sscsToken = sscsToken;
        this.user = user;
        this.userToken = userToken;
        this.userDetails = userDetails;
        this.jursdiction = jurisdiction;
    }
}
