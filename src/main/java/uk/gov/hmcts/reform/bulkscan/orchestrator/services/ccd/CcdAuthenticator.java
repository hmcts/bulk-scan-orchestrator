package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import java.util.function.Supplier;

public class CcdAuthenticator {

    private final String userId;
    private final Supplier<String> serviceTokenSupplier;
    private final String userToken;

    public CcdAuthenticator(
        Supplier<String> serviceTokenSupplier,
        String userId,
        String userToken
    ) {
        this.serviceTokenSupplier = serviceTokenSupplier;
        this.userId = userId;
        this.userToken = userToken;
    }

    public String getUserToken() {
        return this.userToken;
    }

    public String getServiceToken() {
        return this.serviceTokenSupplier.get();
    }

    public String getUserId() {
        return this.userId;
    }
}
