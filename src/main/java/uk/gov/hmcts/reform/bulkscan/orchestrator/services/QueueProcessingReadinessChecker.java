package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AccountLockedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AuthenticationChecker;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static org.springframework.http.HttpStatus.LOCKED;

@Service
public class QueueProcessingReadinessChecker {

    private static final String CHECK_ACCOUNT_NOT_LOCKED_COMMAND_KEY = "check-no-account-locked";
    private static final Logger log = LoggerFactory.getLogger(QueueProcessingReadinessChecker.class);

    private final AuthenticationChecker authenticationChecker;

    // this is for how long the service will assume no accounts are locked in IDAM before it checks again
    private final Duration noAccountLockedCheckValidityDuration;

    // the date by which the service can assume no accounts are locked in IDAM before having to check again
    private LocalDateTime noAccountLockedCheckExpiry = LocalDateTime.now();

    public QueueProcessingReadinessChecker(
        AuthenticationChecker authenticationChecker,
        @Value("${task.check-no-account-locked.check-validity-duration}")
            Duration noAccountLockedCheckValidityDuration
    ) {
        this.authenticationChecker = authenticationChecker;
        this.noAccountLockedCheckValidityDuration = noAccountLockedCheckValidityDuration;
    }

    /**
     * Checks if no jurisdiction-specific account is locked in IDAM.
     * <p>
     * When an account is locked in IDAM, the processing of the queue must be paused,
     * in order to let the account get unlocked (through inactivity).
     * </p>
     *
     * @return true if no account is locked in IDAM. Otherwise, throws AccountLockedException in order to
     *         open the circuit and let Hystrix manage the problem.
     */
    @HystrixCommand(
        commandKey = CHECK_ACCOUNT_NOT_LOCKED_COMMAND_KEY,
        fallbackMethod = "isNoAccountLockedInIdamFallback"
    )
    public boolean isNoAccountLockedInIdam() throws AccountLockedException {
        try {
            if (hasNoAccountLockedCheckExpired()) {
                assertNoAccountIsLockedInIdam();
                updateNoAccountLockedCheckExpiry();
            }

            return true;
        } catch (AccountLockedException e) {
            // let hystrix open the circuit
            throw e;
        } catch (Exception e) {
            // just log the exception, but don't let it block queue processing
            log.error("Failed to check if jurisdictions' accounts are locked in IDAM.", e);
            return true;
        }
    }

    private void assertNoAccountIsLockedInIdam() throws AccountLockedException {
        List<String> jurisdictionsWithLockedAccounts = getJurisdictionsWithLockedAccounts();

        if (!jurisdictionsWithLockedAccounts.isEmpty()) {
            String errorMessage = String.format(
                "Some jurisdictions' accounts are locked in IDAM. "
                    + "This will pause queue processing. Jurisdictions with locked accounts: [%s]",
                String.join(",", jurisdictionsWithLockedAccounts)
            );

            log.error(errorMessage);

            // open the circuit
            throw new AccountLockedException(errorMessage);
        }
    }

    public boolean isNoAccountLockedInIdamFallback() {
        log.warn("Executing fallback method for {} command", CHECK_ACCOUNT_NOT_LOCKED_COMMAND_KEY);
        return false;
    }

    private List<String> getJurisdictionsWithLockedAccounts() {
        return authenticationChecker
            .checkSignInForAllJurisdictions()
            .stream()
            .filter(status -> Objects.equals(status.errorResponseStatus, LOCKED.value()))
            .map(status -> status.jurisdiction)
            .collect(toList());
    }

    private boolean hasNoAccountLockedCheckExpired() {
        return !LocalDateTime.now().isBefore(noAccountLockedCheckExpiry);
    }

    private void updateNoAccountLockedCheckExpiry() {
        this.noAccountLockedCheckExpiry =
            LocalDateTime.now().plus(noAccountLockedCheckValidityDuration);
    }
}
