package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;
import java.util.function.Function;

@Component
public class CaseDataContentBuilderProvider {

    private final CaseDataUpdater caseDataUpdater;

    public CaseDataContentBuilderProvider(CaseDataUpdater caseDataUpdater) {
        this.caseDataUpdater = caseDataUpdater;
    }

    public Function<StartEventResponse, CaseDataContent> getBuilder(
        Map<String, Object> caseData,
        String envelopeId
    ) {
        return startEventResponse ->
            CaseDataContent
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
                        .id(startEventResponse.getEventId())
                        .summary("Case automatically updated with envelope")
                        .description("Case update with envelope " + envelopeId)
                        .build()
                )
                .eventToken(startEventResponse.getToken())
                .build();
    }
}
