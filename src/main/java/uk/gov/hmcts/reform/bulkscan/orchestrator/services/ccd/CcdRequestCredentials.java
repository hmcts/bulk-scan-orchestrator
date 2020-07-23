package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class CcdRequestCredentials {
    final String idamToken;
    final String s2sToken;
    final String userId;

    CcdRequestCredentials(
        String idamToken,
        String s2sToken,
        String userId
    ) {
        this.idamToken = idamToken;
        this.s2sToken = s2sToken;
        this.userId = userId;
    }
}
