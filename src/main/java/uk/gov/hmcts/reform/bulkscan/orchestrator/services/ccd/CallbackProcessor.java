package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

public interface CallbackProcessor {
    List<String> process(String eventType, String eventId, CaseDetails caseDetails);
}
