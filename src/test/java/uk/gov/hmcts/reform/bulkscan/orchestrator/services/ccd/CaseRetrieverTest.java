package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.AUTH_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.SERVICE_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.THE_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever.CASE_TYPE_ID;

@RunWith(MockitoJUnitRunner.class)
public class CaseRetrieverTest {
    @Mock
    private CoreCaseDataApi dataApi;
    @Mock
    private CcdAuthenticatorFactory authenticator;

    private CaseRetriever retriever;

    @Test
    public void should_retrieve_case_successfully() {
        retriever = new CaseRetriever(authenticator, dataApi);

        given(dataApi.readForCaseWorker(USER_TOKEN, SERVICE_TOKEN, USER_ID, JURSIDICTION, CASE_TYPE_ID, CASE_REF))
            .willReturn(THE_CASE);
        given(authenticator.createForJurisdiction(JURSIDICTION)).willReturn(AUTH_DETAILS);

        CaseDetails theCase = retriever.retrieve(JURSIDICTION, CASE_REF);
        assertThat(theCase.getId()).isEqualTo(CASE_ID);
    }

}
