package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.function.Supplier;

public class CcdAuthenticator {

    private final UserDetails userDetails;
    private final Supplier<String> serviceTokenSupplier;
    private final String userToken;

    public CcdAuthenticator(
        Supplier<String> serviceTokenSupplier,
        UserDetails userDetails,
        String userToken
    ) {
        this.serviceTokenSupplier = serviceTokenSupplier;
        this.userDetails = userDetails;
        this.userToken = userToken;
    }

    public String getUserToken() {
        return this.userToken;
    }

    public String getServiceToken() {
        return this.serviceTokenSupplier.get();
    }

    public UserDetails getUserDetails() {
        return this.userDetails;
    }
}
