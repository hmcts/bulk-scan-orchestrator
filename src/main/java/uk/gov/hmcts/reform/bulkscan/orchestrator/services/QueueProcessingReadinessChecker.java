package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AuthenticationChecker;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.LogInAttemptRejectedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
public class QueueProcessingReadinessChecker {

    private static final String LOG_IN_CHECK_COMMAND_KEY = "check-jurisdiction-log-in";
    private static final Logger log = LoggerFactory.getLogger(QueueProcessingReadinessChecker.class);

    private final AuthenticationChecker authenticationChecker;

    // this is for how long the service will assume no log-in attempts are rejected by IDAM before checking again
    private final Duration logInCheckValidityDuration;

    // the date by which the service can assume no log-in attempts are rejected by IDAM before having to check again
    private LocalDateTime logInCheckExpiry = LocalDateTime.now();

    public QueueProcessingReadinessChecker(
        AuthenticationChecker authenticationChecker,
        @Value("${task.check-jurisdiction-log-in.check-validity-duration}")
            Duration logInCheckValidityDuration
    ) {
        this.authenticationChecker = authenticationChecker;
        this.logInCheckValidityDuration = logInCheckValidityDuration;
    }

    /**
     * Checks if no log-in attempt for any jurisdiction-specific account is rejected by IDAM.
     *
     * @return true if IDAM doesn't reject any login attempt.
     *         Otherwise, throws LoginAttemptRejectedException in order to
     *         open the circuit and let Hystrix manage the problem.
     */
    @HystrixCommand(
        commandKey = LOG_IN_CHECK_COMMAND_KEY,
        fallbackMethod = "isNoLogInAttemptRejectedByIdamFallback"
    )
    public boolean isNoLogInAttemptRejectedByIdam() throws LogInAttemptRejectedException {
        try {
            if (hasLogInCheckExpired()) {
                assertNoJurisdictionFailsToLogIn();
                updateLogInCheckExpiry();
            }

            return true;
        } catch (LogInAttemptRejectedException e) {
            // let hystrix open the circuit
            throw e;
        } catch (Exception e) {
            // just log the exception, but don't let it block queue processing
            log.error("Failed to check if jurisdictions' accounts are locked in IDAM.", e);
            return true;
        }
    }

    private void assertNoJurisdictionFailsToLogIn() throws LogInAttemptRejectedException {
        List<String> jurisdictionsFailingToLogIn = getJurisdictionsFailingToLogIn();

        if (!jurisdictionsFailingToLogIn.isEmpty()) {
            String errorMessage = String.format(
                "Login attempts for some jurisdictions' accounts are rejected by IDAM. "
                    + "This will pause queue processing. Jurisdictions: [%s]",
                String.join(",", jurisdictionsFailingToLogIn)
            );

            log.error(errorMessage);

            // open the circuit
            throw new LogInAttemptRejectedException(errorMessage);
        }
    }

    public boolean isNoLogInAttemptRejectedByIdamFallback() {
        log.warn("Executing fallback method for {} command", LOG_IN_CHECK_COMMAND_KEY);
        return false;
    }

    private List<String> getJurisdictionsFailingToLogIn() {
        return authenticationChecker
            .checkSignInForAllJurisdictions()
            .stream()
            // any 4xx error is a rejection
            .filter(status -> status.errorResponseStatus != null && status.errorResponseStatus / 100 == 4)
            .map(status -> status.jurisdiction)
            .collect(toList());
    }

    private boolean hasLogInCheckExpired() {
        return !LocalDateTime.now().isBefore(logInCheckExpiry);
    }

    private void updateLogInCheckExpiry() {
        this.logInCheckExpiry =
            LocalDateTime.now().plus(logInCheckValidityDuration);
    }
}
