package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

public class AuthDetails {
    final UserDetails userDetails;
    final String userToken;
    final String serviceToken;

    public AuthDetails(String serviceToken,
                       String userToken,
                       UserDetails userDetails) {
        this.serviceToken = serviceToken;
        this.userToken = userToken;
        this.userDetails = userDetails;
    }
}
