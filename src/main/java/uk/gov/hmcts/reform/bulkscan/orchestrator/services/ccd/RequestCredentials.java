package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RequestCredentials that = (RequestCredentials) o;
        return Objects.equals(idamToken, that.idamToken)
            && Objects.equals(s2sToken, that.s2sToken)
            && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idamToken, s2sToken, userId);
    }
}
