package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CcdApiSearchTest {

    @Mock CoreCaseDataApi feignCcdApi;
    @Mock CcdAuthenticatorFactory authenticatorFactory;
    @Mock ServiceConfigProvider serviceConfigProvider;

    @Mock CcdAuthenticator ccdAuthenticator;

    @Captor ArgumentCaptor<String> idamTokenCaptor;
    @Captor ArgumentCaptor<String> s2sTokenCaptor;
    @Captor ArgumentCaptor<String> caseTypeCaptor;
    @Captor ArgumentCaptor<String> searchQueryCaptor;

    CcdApi ccdApi;

    @BeforeEach
    void setUp() {
        ccdApi = new CcdApi(feignCcdApi, authenticatorFactory, serviceConfigProvider);
    }

    @Test
    void getCaseRefsByEnvelopeId_should_call_ccd_to_find_cases() {
        // given
        String envelopeId = "abc123";
        String service = "hello";

        String idamToken = "idam-token";
        String s2sToken = "s2s-token";

        var serviceCfg = serviceConfig("some-jurisdiction", asList("case-type-a", "case-type-b"));

        given(serviceConfigProvider.getConfig(service)).willReturn(serviceCfg);

        given(authenticatorFactory.createForJurisdiction(serviceCfg.getJurisdiction())).willReturn(ccdAuthenticator);
        given(ccdAuthenticator.getServiceToken()).willReturn(s2sToken);
        given(ccdAuthenticator.getUserToken()).willReturn(idamToken);

        given(
            feignCcdApi.searchCases(
                idamTokenCaptor.capture(),
                s2sTokenCaptor.capture(),
                caseTypeCaptor.capture(),
                searchQueryCaptor.capture()
            )
        ).willReturn(
            SearchResult
                .builder()
                .cases(asList(
                    CaseDetails.builder().id(111L).build(),
                    CaseDetails.builder().id(222L).build()
                ))
                .build()
        );

        // when
        List<Long> caseRefs = ccdApi.getCaseRefsByEnvelopeId(envelopeId, service);

        // then
        assertThat(caseRefs).containsExactly(111L, 222L);

        assertThat(idamTokenCaptor.getValue()).isEqualTo(idamToken);
        assertThat(s2sTokenCaptor.getValue()).isEqualTo(s2sToken);
        assertThat(caseTypeCaptor.getValue()).isEqualTo("case-type-a,case-type-b");
        assertThat(searchQueryCaptor.getValue()).contains(envelopeId);
    }

    private ServiceConfigItem serviceConfig(String jurisdiction, List<String> caseTypes) {
        var cfg = new ServiceConfigItem();
        cfg.setJurisdiction(jurisdiction);
        cfg.setCaseTypeIds(caseTypes);
        return cfg;
    }
}
