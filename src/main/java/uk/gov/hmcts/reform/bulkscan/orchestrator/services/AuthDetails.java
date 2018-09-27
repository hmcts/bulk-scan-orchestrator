package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

public class AuthDetails {
    final UserAuthDetails userAuthDetails;
    final String serviceToken;

    public AuthDetails(String serviceToken,
                       UserAuthDetails userAuthDetails) {
        this.serviceToken = serviceToken;
        this.userAuthDetails = userAuthDetails;
    }

    public static AuthDetails from(String serviceToken, UserDetails userDetails, String userToken) {
        return new AuthDetails(serviceToken,new UserAuthDetails(userDetails,userToken));
    }
}
