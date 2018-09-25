package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

class CcdAuthInfo {
    final UserDetails userDetails;
    final String sscsToken;
    final Credential user;
    final String userToken;

    CcdAuthInfo(String sscsToken, Credential user, String userToken, UserDetails userDetails) {
        this.sscsToken = sscsToken;
        this.user = user;
        this.userToken = userToken;
        this.userDetails = userDetails;
    }
}
