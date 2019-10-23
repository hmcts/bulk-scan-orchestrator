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
    private static final Logger LOG = LoggerFactory.getLogger(PaymentsProcessor.class);

    private final IPaymentsPublisher paymentsPublisher;

    public PaymentsProcessor(
        IPaymentsPublisher paymentsPublisher
    ) {
        this.paymentsPublisher = paymentsPublisher;
    }

    public void processPayments(Envelope envelope, Long ccdId, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {
            CreatePaymentsCommand cmd = new CreatePaymentsCommand(
                envelope.id,
                Long.toString(ccdId),
                envelope.jurisdiction,
                envelope.container,
                envelope.poBox,
                isExceptionRecord,
                envelope.payments.stream()
                    .map(payment -> new PaymentData(payment.documentControlNumber))
                    .collect(toList())
            );

            LOG.info("Started processing payments for case with CCD reference {}", cmd.ccdReference);
            paymentsPublisher.send(cmd);
            LOG.info("Finished processing payments for case with CCD reference {}", cmd.ccdReference);
        }
    }

    public void updatePayments(CaseDetails exceptionRecord, long newCaseId) {

        boolean containsPayments =
            Objects.equals(
                exceptionRecord.getData().get(ExceptionRecordFields.CONTAINS_PAYMENTS),
                YesNoFieldValues.YES
            );

        if (containsPayments) {
            String envelopeId = exceptionRecord.getData().get(ExceptionRecordFields.ENVELOPE_ID).toString();
            String jurisdiction = exceptionRecord.getData().get(ExceptionRecordFields.PO_BOX_JURISDICTION).toString();

            LOG.info(
                "Sending payment update message. ER id: {}",
                exceptionRecord.getId()
            );

            paymentsPublisher.send(
                new UpdatePaymentsCommand(
                    Long.toString(exceptionRecord.getId()),
                    Long.toString(newCaseId),
                    envelopeId,
                    jurisdiction
                )
            );
        } else {
            LOG.info(
                "Exception record has no payments, not sending update command. ER id: {}",
                exceptionRecord.getId()
            );
        }
    }
}
