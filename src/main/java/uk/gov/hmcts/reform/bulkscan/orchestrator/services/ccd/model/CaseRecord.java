package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

public interface CaseRecord extends CaseDataCreator {

    @FunctionalInterface
    interface Construct {
        CaseDataCreator apply(Envelope envelope, CaseDetails caseDetails);
    }

    enum Record {
        EXCEPTION_RECORD,
        SUPPLEMENTARY_EVIDENCE,
    }

    Record getCaseRecordIdentifier();
}
