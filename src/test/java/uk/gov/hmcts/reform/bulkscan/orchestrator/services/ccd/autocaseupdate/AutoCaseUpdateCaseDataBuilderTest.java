package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AutoCaseUpdateCaseDataBuilderTest {

    @Mock Map<String, Object> inputCaseData;
    @Mock Map<String, Object> caseDataAfterUpdate;

    @Mock CaseDataUpdater caseDataUpdater;

    AutoCaseUpdateCaseDataBuilder caseDataBuilder;

    @BeforeEach
    void setUp() {
        caseDataBuilder = new AutoCaseUpdateCaseDataBuilder(caseDataUpdater);
    }

    @Test
    void should_update_envelope_references_and_build_case_data_content() {
        // given
        String envelopeId = "123456";
        String eventId = "abc-def";
        String eventToken = "token-token-token";

        given(caseDataUpdater.updateEnvelopeReferences(inputCaseData, envelopeId, CaseAction.UPDATE))
            .willReturn(caseDataAfterUpdate);

        // when
        CaseDataContent result = caseDataBuilder.getCaseDataContent(inputCaseData, envelopeId, eventId, eventToken);

        // then
        assertThat(result.getData()).isEqualTo(caseDataAfterUpdate);
        assertThat(result.getEventToken()).isEqualTo(eventToken);
        assertThat(result.getEvent().getId()).isEqualTo(eventId);
    }
}
