package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AuthenticationChecker;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class IdamConfigStatusEndpointTest {

    @Mock
    private AuthenticationChecker authenticationChecker;

    private IdamConfigStatusEndpoint endpoint;

    @Before
    public void setUp() {
        endpoint = new IdamConfigStatusEndpoint(authenticationChecker);
    }

    @Test
    public void jurisdiction_should_call_authenticator_to_get_result() {
        // given
        String jurisdiction = "jurisdiction1";
        JurisdictionConfigurationStatus expectedStatus = mock(JurisdictionConfigurationStatus.class);
        willReturn(expectedStatus).given(authenticationChecker).checkSignInForJurisdiction(jurisdiction);

        // when
        JurisdictionConfigurationStatus returnedStatus = endpoint.jurisdiction(jurisdiction);

        // then
        assertThat(returnedStatus).isSameAs(expectedStatus);
        verify(authenticationChecker).checkSignInForJurisdiction(jurisdiction);
    }

    @Test
    public void jurisdictions_should_call_authenticator_to_get_result() {
        // given
        List<JurisdictionConfigurationStatus> expectedStatuses =
            Arrays.asList(mock(JurisdictionConfigurationStatus.class));

        willReturn(expectedStatuses).given(authenticationChecker).checkSignInForAllJurisdictions();

        // when
        List<JurisdictionConfigurationStatus> returnedStatuses = endpoint.jurisdictions();

        // then
        assertThat(returnedStatuses).isEqualTo(expectedStatuses);
        verify(authenticationChecker).checkSignInForAllJurisdictions();
    }
}
