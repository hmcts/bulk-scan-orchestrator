package uk.gov.hmcts.reform.bulkscan.orchestrator.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AuthenticationChecker;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IdamHealthIndicatorTest {

    @Mock
    private AuthenticationChecker authenticationChecker;

    private IdamHealthIndicator idamHealthIndicator;

    @BeforeEach
    void setUp() {
        idamHealthIndicator = new IdamHealthIndicator(authenticationChecker);
    }

    @Test
    void should_return_up_when_all_jurisdictions_are_configured_correctly() {
        given(authenticationChecker.checkSignInForAllJurisdictions()).willReturn(List.of(
            new JurisdictionConfigurationStatus("bulkscan", true),
            new JurisdictionConfigurationStatus("probate", true)
        ));

        assertThat(idamHealthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_return_down_when_any_jurisdiction_is_not_configured_correctly() {
        given(authenticationChecker.checkSignInForAllJurisdictions()).willReturn(List.of(
            new JurisdictionConfigurationStatus("bulkscan", true),
            new JurisdictionConfigurationStatus("probate", false)
        ));

        assertThat(idamHealthIndicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
