package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AuthenticationChecker;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@IntegrationTest
public class QueueProcessingReadinessCheckerTest {

    private static final List<JurisdictionConfigurationStatus> STATUS_WITHOUT_REJECTIONS =
        ImmutableList.of(
            new JurisdictionConfigurationStatus(
                "jurisdiction1",
                false,
                "serverError",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            ),
            new JurisdictionConfigurationStatus(
                "jurisdiction2",
                true
            )
        );

    private static final List<JurisdictionConfigurationStatus> STATUS_WITH_REJECTION =
        ImmutableList.of(
            new JurisdictionConfigurationStatus(
                "jurisdiction1",
                false,
                "locked",
                HttpStatus.LOCKED.value()
            )
        );

    @SpyBean
    private AuthenticationChecker authenticationChecker;

    @Autowired
    private QueueProcessingReadinessChecker queueProcessingReadinessChecker;

    //    @Test
    //    public void should_start_returning_false_when_idam_rejects_log_in_attempt() throws Exception {
    //        given(authenticationChecker.checkSignInForAllJurisdictions())
    //            .willReturn(STATUS_WITHOUT_REJECTIONS)
    //            .willReturn(STATUS_WITH_REJECTION)
    //            .willReturn(STATUS_WITHOUT_REJECTIONS);
    //
    //        assertThat(queueProcessingReadinessChecker.isNoLogInAttemptRejectedByIdam()).isTrue();
    //        assertThat(queueProcessingReadinessChecker.isNoLogInAttemptRejectedByIdam()).isFalse();
    //        assertThat(queueProcessingReadinessChecker.isNoLogInAttemptRejectedByIdam()).isTrue();
    //
    //        verify(authenticationChecker, times(3)).checkSignInForAllJurisdictions();
    //    }

    @Test
    public void should_keep_returning_true_when_idam_accepts_login_attempts() throws Exception {
        given(authenticationChecker.checkSignInForAllJurisdictions())
            .willReturn(STATUS_WITHOUT_REJECTIONS);

        int numberOfChecks = 10;

        for (int i = 0; i < numberOfChecks; i++) {
            assertThat(queueProcessingReadinessChecker.isNoLogInAttemptRejectedByIdam()).isTrue();
        }

        verify(authenticationChecker, times(numberOfChecks)).checkSignInForAllJurisdictions();
    }
}
