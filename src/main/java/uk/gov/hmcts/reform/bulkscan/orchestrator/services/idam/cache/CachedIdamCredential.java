package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

public class CachedIdamCredential {

    public final String accessToken;
    public final UserDetails userDetails;
    public final long expiresIn;

    public CachedIdamCredential(String accessToken, UserDetails userDetails, long expiresIn) {
        this.accessToken = accessToken;
        this.userDetails = userDetails;
        this.expiresIn = expiresIn;
    }
}
