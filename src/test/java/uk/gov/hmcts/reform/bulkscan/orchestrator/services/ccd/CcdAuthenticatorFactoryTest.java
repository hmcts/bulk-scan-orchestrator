package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.CachedIdamCredential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.IdamCachedClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.SERVICE_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_TOKEN;

@ExtendWith(MockitoExtension.class)
class CcdAuthenticatorFactoryTest {

    @Mock
    private AuthTokenGenerator tokenGenerator;
    @Mock
    private IdamCachedClient idamClient;

    private CcdAuthenticatorFactory service;

    @BeforeEach
    void before() {
        service = new CcdAuthenticatorFactory(tokenGenerator, idamClient);
    }

    @Test
    void should_sucessfully_return_authInfo() {
        CachedIdamCredential cachedIdamCredential = new CachedIdamCredential(
            USER_TOKEN,
            USER_DETAILS,
            28800L
        );

        given(tokenGenerator.generate()).willReturn(SERVICE_TOKEN);
        given(idamClient.getIdamCredentials(JURSIDICTION)).willReturn(cachedIdamCredential);

        CcdAuthenticator authenticator = service.createForJurisdiction(JURSIDICTION);

        assertThat(authenticator.getServiceToken()).isEqualTo(SERVICE_TOKEN);
        assertThat(authenticator.getUserToken()).isEqualTo(USER_TOKEN);
        assertThat(authenticator.getUserDetails().getId()).isEqualTo(USER_ID);
    }

}
