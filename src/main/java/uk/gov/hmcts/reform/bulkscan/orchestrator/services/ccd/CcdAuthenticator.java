package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.function.Supplier;

public class CcdAuthenticator {
    private final UserDetails userDetails;
    private final Supplier<String> serviceTokenSupplier;
    private final Supplier<String> userTokenSupplier;

    public CcdAuthenticator(
        Supplier<String> serviceTokenSupplier,
        UserDetails userDetails,
        Supplier<String> userTokenSupplier
    ) {
        this.serviceTokenSupplier = serviceTokenSupplier;
        this.userDetails = userDetails;
        this.userTokenSupplier = userTokenSupplier;
    }

    public static CcdAuthenticator from(
        Supplier<String> serviceTokenSupplier,
        UserDetails userDetails,
        Supplier<String> userTokenSupplier) {
        return new CcdAuthenticator(serviceTokenSupplier, userDetails, userTokenSupplier);
    }

    public String getUserToken() {
        return this.userTokenSupplier.get();
    }

    public String getServiceToken() {
        return this.serviceTokenSupplier.get();
    }

    public UserDetails getUserDetails() {
        return this.userDetails;
    }
}
