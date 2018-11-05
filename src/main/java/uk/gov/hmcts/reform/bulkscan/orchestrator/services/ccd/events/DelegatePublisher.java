package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

public class DelegatePublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DelegatePublisher.class);

    private static final String EXCEPTION_RECORD_CASE_TYPE = "ExceptionRecord";

    private final EventPublisher publisher;

    private final String caseTypeId;

    DelegatePublisher(EventPublisher publisher, CaseDetails caseDetails) {
        this.publisher = publisher;
        this.caseTypeId = caseDetails == null ? EXCEPTION_RECORD_CASE_TYPE : caseDetails.getCaseTypeId();
    }

    @Override
    public void publish(Envelope envelope, String caseTypeId) {
        if (publisher != null) {
            publisher.publish(envelope, this.caseTypeId);
        } else {
            log.info(
                "Skipped processing of envelope ID {} for case {} - classification {} not handled yet",
                envelope.id,
                envelope.caseRef,
                envelope.classification
            );
        }
    }

    // only used in tests to verify which publisher is selected
    EventPublisher getDelegatedClass() {
        return publisher;
    }
}
