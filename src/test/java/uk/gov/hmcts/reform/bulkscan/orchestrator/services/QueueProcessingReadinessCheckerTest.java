package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AccountLockedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AuthenticationChecker;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class QueueProcessingReadinessCheckerTest {

    @Mock
    private AuthenticationChecker authenticationChecker;

    private QueueProcessingReadinessChecker processingReadinessChecker;

    @BeforeEach
    public void setUp() {
        processingReadinessChecker =
            new QueueProcessingReadinessChecker(authenticationChecker, Duration.ofMinutes(1));
    }

    @Test
    public void isNoAccountLockedInIdam_returns_true_when_no_account_is_locked() throws Exception {
        List<JurisdictionConfigurationStatus> noAccountLockedStatus = Arrays.asList(
            new JurisdictionConfigurationStatus("jurisdiction1", true, null, 200),
            new JurisdictionConfigurationStatus("jurisdiction2", false, "forbidden", 403)
        );

        given(authenticationChecker.checkSignInForAllJurisdictions()).willReturn(noAccountLockedStatus);

        assertThat(processingReadinessChecker.isNoAccountLockedInIdam()).isTrue();
    }

    @Test
    public void isNoAccountLockedInIdam_throws_exception_when_an_account_is_locked() {
        List<JurisdictionConfigurationStatus> oneAccountLockedStatus = Arrays.asList(
            new JurisdictionConfigurationStatus("jurisdiction1", true, null, 200),
            new JurisdictionConfigurationStatus("jurisdiction2", false, "forbidden", 403),
            new JurisdictionConfigurationStatus("jurisdiction3", false, "locked", 423)
        );

        given(authenticationChecker.checkSignInForAllJurisdictions()).willReturn(oneAccountLockedStatus);

        String expectedErrorMessage = "Some jurisdictions' accounts are locked in IDAM. "
            + "This will pause queue processing. Jurisdictions with locked accounts: [jurisdiction3]";

        assertThatThrownBy(() -> processingReadinessChecker.isNoAccountLockedInIdam())
            .isInstanceOf(AccountLockedException.class)
            .hasMessage(expectedErrorMessage);
    }

    @Test
    public void isNoAccountLockedInIdamFallback_returns_false() {
        assertThat(
            processingReadinessChecker.isNoAccountLockedInIdamFallback()
        ).isFalse();
    }

}
