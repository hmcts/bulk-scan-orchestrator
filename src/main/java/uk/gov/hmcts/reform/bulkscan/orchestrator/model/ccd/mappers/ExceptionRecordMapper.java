package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component
public class ExceptionRecordMapper extends ModelMapper<ExceptionRecord> {

    public ExceptionRecordMapper() {
        // empty mapper construct
    }

    @Override
    public ExceptionRecord fromEnvelope(Envelope envelope) {
        return new ExceptionRecord(
            envelope.classification.name(),
            envelope.poBox,
            envelope.jurisdiction,
            getLocalDateTime(envelope.deliveryDate),
            getLocalDateTime(envelope.openingDate),
            mapDocuments(envelope.documents)
        );
    }
}
