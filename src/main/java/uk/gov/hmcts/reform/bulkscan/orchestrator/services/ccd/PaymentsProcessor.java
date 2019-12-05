package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Objects;

import static java.util.stream.Collectors.toList;

@Service
public class PaymentsProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentsProcessor.class);

    private final IPaymentsPublisher paymentsPublisher;

    public PaymentsProcessor(IPaymentsPublisher paymentsPublisher) {
        this.paymentsPublisher = paymentsPublisher;
    }

    public void createPayments(Envelope envelope, Long caseId, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {
            CreatePaymentsCommand cmd = new CreatePaymentsCommand(
                envelope.id,
                Long.toString(caseId),
                envelope.jurisdiction,
                envelope.container,
                envelope.poBox,
                isExceptionRecord,
                envelope.payments.stream()
                    .map(payment -> new PaymentData(payment.documentControlNumber))
                    .collect(toList())
            );

            log.info("Started processing payments for case with CCD reference {}", cmd.ccdReference);
            paymentsPublisher.send(cmd);
            log.info("Finished processing payments for case with CCD reference {}", cmd.ccdReference);
        } else {
            log.info(
                "Envelope has no payments, not sending create command. Envelope id: {}",
                envelope.id
            );
        }
    }

    public void updatePayments(CaseDetails exceptionRecord, long newCaseId) {
        boolean containsPayments =
            Objects.equals(
                exceptionRecord.getData().get(ExceptionRecordFields.CONTAINS_PAYMENTS),
                YesNoFieldValues.YES
            );

        if (containsPayments) {

            log.info("Contains Payments, send payment update message. ER id: {}", exceptionRecord.getId());

            String envelopeId = exceptionRecord.getData().get(ExceptionRecordFields.ENVELOPE_ID).toString();
            String jurisdiction = exceptionRecord.getData().get(ExceptionRecordFields.PO_BOX_JURISDICTION).toString();

            paymentsPublisher.send(
                new UpdatePaymentsCommand(
                    Long.toString(exceptionRecord.getId()),
                    Long.toString(newCaseId),
                    envelopeId,
                    jurisdiction
                )
            );
            log.info("Finished sending payment update message. ER id: {}", exceptionRecord.getId());

        } else {
            log.info(
                "Exception record has no payments, not sending update command. ER id: {}",
                exceptionRecord.getId()
            );
        }
    }
}
