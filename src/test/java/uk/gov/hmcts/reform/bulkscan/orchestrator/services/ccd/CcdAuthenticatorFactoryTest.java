package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.PASSWORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.SERVICE_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_CREDS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_NAME;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_TOKEN;


@RunWith(MockitoJUnitRunner.class)
public class CcdAuthenticatorFactoryTest {
    @Mock
    private AuthTokenGenerator tokenGenerator;
    @Mock
    private IdamClient idamClient;
    @Mock
    private JurisdictionToUserMapping users;

    private CcdAuthenticatorFactory service;

    @Before
    public void before() {
        service = new CcdAuthenticatorFactory(tokenGenerator, idamClient, users);
    }

    @Test
    public void should_sucessfully_return_authInfo() {
        given(users.getUser(eq(JURSIDICTION))).willReturn(USER_CREDS);
        given(tokenGenerator.generate()).willReturn(SERVICE_TOKEN);
        given(idamClient.authenticateUser(eq(USER_NAME), eq(PASSWORD))).willReturn(USER_TOKEN);
        given(idamClient.getUserDetails(USER_TOKEN)).willReturn(USER_DETAILS);

        CcdAuthenticator authenticator = service.createForJurisdiction(JURSIDICTION);

        assertThat(authenticator.getServiceToken()).isEqualTo(SERVICE_TOKEN);
        assertThat(authenticator.getUserToken()).isEqualTo(USER_TOKEN);
        assertThat(authenticator.userDetails.getId()).isEqualTo(USER_ID);
    }

}
