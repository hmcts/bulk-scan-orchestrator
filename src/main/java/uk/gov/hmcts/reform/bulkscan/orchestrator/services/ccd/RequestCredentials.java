package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class RequestCredentials {
    final String idamToken;
    String s2sToken;
    final String userId;

    public RequestCredentials(String idamToken, String userId) {
        this.idamToken = idamToken;
        this.userId = userId;
    }

    void setS2sToken(String s2sToken) {
        this.s2sToken = s2sToken;
    }
}
