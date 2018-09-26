package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

public class CcdAuthInfo {
    final UserDetails userDetails;
    final String userToken;
    final String serviceToken;
    final String jurisdiction;

    public CcdAuthInfo(String serviceToken,
                       String userToken,
                       UserDetails userDetails,
                       String jurisdiction) {
        this.serviceToken = serviceToken;
        this.userToken = userToken;
        this.userDetails = userDetails;
        this.jurisdiction = jurisdiction;
    }
}
