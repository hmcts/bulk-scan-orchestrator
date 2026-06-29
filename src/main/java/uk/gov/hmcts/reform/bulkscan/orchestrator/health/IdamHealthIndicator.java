package uk.gov.hmcts.reform.bulkscan.orchestrator.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AuthenticationChecker;

import java.util.List;

@Component("idam")
public class IdamHealthIndicator implements HealthIndicator {

    private final AuthenticationChecker authenticationChecker;

    public IdamHealthIndicator(AuthenticationChecker authenticationChecker) {
        this.authenticationChecker = authenticationChecker;
    }

    @Override
    public Health health() {
        List<JurisdictionConfigurationStatus> statuses = authenticationChecker.checkSignInForAllJurisdictions();

        return statuses.stream().allMatch(status -> status.isCorrect)
            ? Health.up().build()
            : Health.down().build();
    }
}
