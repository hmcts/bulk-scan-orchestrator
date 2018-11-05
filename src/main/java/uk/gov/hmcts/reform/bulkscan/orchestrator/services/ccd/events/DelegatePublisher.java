package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

public class DelegatePublisher implements EventPublisher {

    private static final String EXCEPTION_RECORD_CASE_TYPE = "ExceptionRecord";

    private final EventPublisher publisher;

    private final String caseTypeId;

    DelegatePublisher(EventPublisher publisher, CaseDetails caseDetails) {
        this.publisher = publisher;
        this.caseTypeId = caseDetails == null ? EXCEPTION_RECORD_CASE_TYPE : caseDetails.getCaseTypeId();
    }

    @Override
    public void publish(Envelope envelope, String caseTypeId) {
        publisher.publish(envelope, this.caseTypeId);
    }
}
