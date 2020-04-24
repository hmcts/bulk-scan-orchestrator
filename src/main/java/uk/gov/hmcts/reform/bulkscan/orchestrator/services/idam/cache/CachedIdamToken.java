package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

public class CachedIdamToken {

    public final String accessToken;
    public final long expiresIn;

    public CachedIdamToken(String accessToken, long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }
}
