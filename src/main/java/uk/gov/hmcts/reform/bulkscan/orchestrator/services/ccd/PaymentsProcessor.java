package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.launchdarkly.LaunchDarklyClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;

import java.util.Optional;

import static java.util.stream.Collectors.toList;

//TODO: This class can likely be removed upon being fully migrated to API route - paymentsService holds the logic
@Service
public class PaymentsProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentsProcessor.class);

    private final IPaymentsPublisher paymentsPublisher;

    private final LaunchDarklyClient launchDarklyClient;

    private final PaymentsService paymentsService;

    public PaymentsProcessor(IPaymentsPublisher paymentsPublisher, LaunchDarklyClient launchDarklyClient,
                             PaymentsService paymentsService) {
        this.paymentsPublisher = paymentsPublisher;
        this.launchDarklyClient = launchDarklyClient;
        this.paymentsService = paymentsService;
    }

    public void createPayments(Envelope envelope, Long caseId, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {
            //TODO: Remove flag once fully migrated to API route
            if (launchDarklyClient.isFeatureEnabled("process-payment-via-api")) {
                log.info("Started processing new payment via API, envelope id: {}, ccd reference: {}",
                    envelope.id, caseId);
                paymentsService.createNewPayment(envelope, isExceptionRecord, caseId);
                log.info("Finished processing new payment via API, envelope id: {}, ccd reference: {}",
                    envelope.id, caseId);
            } else {
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
            }
        } else {
            log.info(
                "Envelope has no payments, not sending create command. Envelope id: {}. Case reference {}",
                envelope.id,
                Optional.ofNullable(envelope.caseRef).orElse("(NOT PRESENT)")
            );
        }
    }

    public void updatePayments(
        PaymentsHelper paymentsHelper,
        String exceptionRecordId,
        String jurisdiction,
        String newCaseId) {
        if (paymentsHelper.containsPayments) {
            //TODO: Remove flag once fully migrated to API route
            if (launchDarklyClient.isFeatureEnabled("process-payment-via-api")) {
                log.info("Started processing update payment via API");
                paymentsService.updatePayment(paymentsHelper.envelopeId, jurisdiction, exceptionRecordId, newCaseId);
                log.info("Finished processing update payment via API");
            } else {
                log.info("Contains Payments, sending payment update message. ER id: {}", exceptionRecordId);

                paymentsPublisher.send(
                    new UpdatePaymentsCommand(
                        exceptionRecordId,
                        newCaseId,
                        paymentsHelper.envelopeId,
                        jurisdiction
                    )
                );
                log.info("Finished sending payment update message. ER id: {}", exceptionRecordId);
            }
        } else {
            log.info(
                "Exception record has no payments, not sending update command. ER id: {}",
                exceptionRecordId
            );
        }
    }
}
