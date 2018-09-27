package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

public class UserAuthDetails {
    final UserDetails details;
    final String token;

    public UserAuthDetails(UserDetails details, String token) {
        this.details = details;
        this.token = token;
    }
}
