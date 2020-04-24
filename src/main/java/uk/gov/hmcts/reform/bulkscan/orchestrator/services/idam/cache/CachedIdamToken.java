package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

public class CachedIdamToken {

    public final String token;
    public final long expiresIn;

    public CachedIdamToken(String token, long expiresIn) {
        this.token = token;
        this.expiresIn = expiresIn;
    }
}
