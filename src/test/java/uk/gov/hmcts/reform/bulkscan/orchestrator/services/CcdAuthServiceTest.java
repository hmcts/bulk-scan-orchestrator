package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.PASSWORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.SSCS_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.USER_CREDS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.USER_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.USER_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.USER_NAME;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.USER_TOKEN;


@RunWith(MockitoJUnitRunner.class)
public class CcdAuthServiceTest {

    @Mock
    private AuthTokenGenerator tokenGenerator;
    @Mock
    private IdamClient idamClient;
    @Mock
    private JurisdictionToUserMapping users;
    private CcdAuthService service;

    @Before
    public void before() {
        service = new CcdAuthService(tokenGenerator, idamClient, users);
        when(users.getUser(eq(JURSIDICTION))).thenReturn(USER_CREDS);
        when(tokenGenerator.generate()).thenReturn(SSCS_TOKEN);
        when(idamClient.authenticateUser(eq(USER_NAME), eq(PASSWORD))).thenReturn(USER_TOKEN);
        when(idamClient.getUserDetails(USER_TOKEN)).thenReturn(USER_DETAILS);
    }

    @Test
    public void should_sucessfully_return_authInfo() {
        CcdAuthInfo authInfo = service.authenticateForJurisdiction(JURSIDICTION);
        assertThat(authInfo).isNotNull();
        assertThat(authInfo.sscsToken).isEqualTo(SSCS_TOKEN);
        assertThat(authInfo.userToken).isEqualTo(USER_TOKEN);
        assertThat(authInfo.userDetails.getId()).isEqualTo(USER_ID);
    }

}
