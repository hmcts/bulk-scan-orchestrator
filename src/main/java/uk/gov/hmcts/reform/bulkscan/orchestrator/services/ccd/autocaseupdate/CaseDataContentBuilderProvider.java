package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;
import java.util.function.Function;

@Component
public class CaseDataContentBuilderProvider {

    public Function<StartEventResponse, CaseDataContent> getBuilder(
        Map<String, Object> finalCaseData,
        String envelopeId
    ) {
        return startEventResponse ->
            CaseDataContent
                .builder()
                .data(finalCaseData)
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
