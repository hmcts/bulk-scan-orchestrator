package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;

import java.util.Map;

@Component
public class AutoCaseUpdateCaseDataBuilder {

    private final CaseDataUpdater caseDataUpdater;

    public AutoCaseUpdateCaseDataBuilder(CaseDataUpdater caseDataUpdater) {
        this.caseDataUpdater = caseDataUpdater;
    }

    public CaseDataContent getCaseDataContent(
        Map<String, Object> caseData,
        String envelopeId,
        String eventId,
        String eventToken
    ) {
        return CaseDataContent
            .builder()
            .data(
                caseDataUpdater
                    .updateEnvelopeReferences(
                        caseData,
                        envelopeId,
                        CaseAction.UPDATE
                    ))
            .event(
                Event
                    .builder()
                    .id(eventId)
                    .summary("Case automatically updated with envelope")
                    .description("Case update with envelope " + envelopeId)
                    .build()
            )
            .eventToken(eventToken)
            .build();
    }
}
