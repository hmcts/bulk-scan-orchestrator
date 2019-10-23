package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Objects;

@Component
public class PaymentsHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentsHandler.class);

    private final IPaymentsPublisher paymentsPublisher;

    public PaymentsHandler(IPaymentsPublisher paymentsPublisher) {
        this.paymentsPublisher = paymentsPublisher;
    }

    public void handlePayments(CaseDetails exceptionRecord, long newCaseId) {

        boolean containsPayments =
            Objects.equals(
                exceptionRecord.getData().get(ExceptionRecordFields.CONTAINS_PAYMENTS),
                YesNoFieldValues.YES
            );

        if (containsPayments) {
            String envelopeId = exceptionRecord.getData().get(ExceptionRecordFields.ENVELOPE_ID).toString();
            String jurisdiction = exceptionRecord.getData().get(ExceptionRecordFields.PO_BOX_JURISDICTION).toString();

            log.info(
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
            log.info(
                "Exception record has no payments, not sending update command. ER id: {}",
                exceptionRecord.getId()
            );
        }
    }
}
