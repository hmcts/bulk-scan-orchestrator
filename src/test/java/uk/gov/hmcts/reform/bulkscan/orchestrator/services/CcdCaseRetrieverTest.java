package uk.gov.hmcts.reform.bulkscan.orchestrator.services;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.CCD_AUTH_INFO;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.SSCS_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.THE_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.USER_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.TestData.USER_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.CcdCaseRetriever.CASE_TYPE_ID;

@RunWith(MockitoJUnitRunner.class)
public class CcdCaseRetrieverTest {
    @Mock
    private CoreCaseDataApi dataApi;

    private CcdCaseRetriever retriever;

    @Test
    public void should_retrieve_case_successfully() {
        retriever = new CcdCaseRetriever(dataApi);
        when(dataApi.readForCaseWorker(USER_TOKEN, SSCS_TOKEN, USER_ID, JURSIDICTION, CASE_TYPE_ID, CASE_REF))
            .thenReturn(THE_CASE);
        CaseDetails theCase = retriever.retrieve(CCD_AUTH_INFO, CASE_REF);
        assertThat(theCase.getId()).isEqualTo(CASE_ID);
    }

}
