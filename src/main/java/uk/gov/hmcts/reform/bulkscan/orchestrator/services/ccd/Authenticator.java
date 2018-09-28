package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.function.Supplier;

public class Authenticator {
    final UserDetails userDetails;
    private final Supplier<String> serviceTokenSupplier;
    private final Supplier<String> userTokenSupplier;

    public Authenticator(
        Supplier<String> serviceTokenSupplier,
        UserDetails userDetails,
        Supplier<String> userTokenSupplier
    ) {
        this.serviceTokenSupplier = serviceTokenSupplier;
        this.userDetails = userDetails;
        this.userTokenSupplier = userTokenSupplier;
    }

    public static Authenticator from(
        Supplier<String> serviceTokenSupplier,
        UserDetails userDetails,
        Supplier<String> userTokenSupplier) {
        return new Authenticator(serviceTokenSupplier, userDetails, userTokenSupplier);
    }

    public String getUserToken() {
        return this.userTokenSupplier.get();
    }

    public String getServiceToken() {
        return this.serviceTokenSupplier.get();
    }

}
