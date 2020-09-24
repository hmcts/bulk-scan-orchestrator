package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CaseDataContentBuilderProviderTest {

    @Mock Map<String, Object> inputCaseData;
    @Mock Map<String, Object> caseDataAfterUpdate;

    @Mock CaseDataUpdater caseDataUpdater;

    CaseDataContentBuilderProvider caseDataBuilderProvider;

    @BeforeEach
    void setUp() {
        caseDataBuilderProvider = new CaseDataContentBuilderProvider(caseDataUpdater);
    }

    @Test
    void should_provide_function_that_builds_case_data_content() {
        // given
        String envelopeId = "123456";
        String eventId = "abc-def";
        String eventToken = "token-token-token";
        var startEventResponse = mock(StartEventResponse.class);
        given(startEventResponse.getEventId()).willReturn(eventId);
        given(startEventResponse.getToken()).willReturn(eventToken);

        given(caseDataUpdater.updateEnvelopeReferences(inputCaseData, envelopeId, CaseAction.UPDATE))
            .willReturn(caseDataAfterUpdate);

        // when
        Function<StartEventResponse, CaseDataContent> builder = caseDataBuilderProvider.getBuilder(inputCaseData, envelopeId);
        CaseDataContent result = builder.apply(startEventResponse);

        // then
        assertThat(result.getData()).isEqualTo(caseDataAfterUpdate);
        assertThat(result.getEventToken()).isEqualTo(eventToken);
        assertThat(result.getEvent().getId()).isEqualTo(eventId);
    }
}
