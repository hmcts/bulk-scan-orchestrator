package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.caserecord;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.CaseRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Classification;

public class ExceptionRecord implements CaseRecord {

    private final Envelope envelope;
    private final CaseDetails caseDetails;

    public ExceptionRecord(Envelope envelope, CaseDetails caseDetails) {
        this.envelope = envelope;
        this.caseDetails = caseDetails;
    }

    @Override
    public CaseDataContent createDataContent(
        Classification classification,
        String eventToken,
        boolean ignoreWarning
    ) {
        return null;
    }

    @Override
    public Record getCaseRecordIdentifier() {
        return Record.EXCEPTION_RECORD;
    }
}
