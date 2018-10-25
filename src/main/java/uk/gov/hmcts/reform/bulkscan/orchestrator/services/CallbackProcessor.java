package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

public interface CallbackProcessor {
    List<String> process(String type, String eventId, CaseDetails caseDetails);
}
