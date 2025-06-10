package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.internal.PaymentProcessorClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.database.PaymentsRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.database.UpdatePaymentsRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.NotFoundException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.PaymentReprocessFailedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.CreatePaymentDTO;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.PaymentInfoDTO;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.PaymentStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.UpdatePaymentDTO;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PaymentsService {

    private final PaymentProcessorClient paymentProcessorClient;
    private final PaymentsRepository paymentsRepository;
    private final UpdatePaymentsRepository updatePaymentsRepository;

    public PaymentsService(PaymentProcessorClient paymentProcessorClient, PaymentsRepository paymentsRepository,
                           UpdatePaymentsRepository updatePaymentsRepository) {
        this.paymentProcessorClient = paymentProcessorClient;
        this.paymentsRepository = paymentsRepository;
        this.updatePaymentsRepository = updatePaymentsRepository;
    }

    /**
     * Creates a new payment in the database, sends it to payment processor.
     *
     * @param envelope The envelope containing the payment data.
     * @param isExceptionRecord boolean for if the envelope being processed is an exception record.
     * @param caseId The id of the CCD case.
     */
    public void createNewPayment(Envelope envelope, boolean isExceptionRecord, Long caseId) {
        CreatePaymentDTO paymentToCreate = new CreatePaymentDTO(
            envelope.id,
            Long.toString(caseId),
            isExceptionRecord,
            envelope.poBox,
            envelope.jurisdiction,
            envelope.container,
            envelope.payments.stream().map(payment ->
                new PaymentInfoDTO(payment.documentControlNumber)).toList()
        );

        Payment payment = paymentsRepository.save(new Payment(paymentToCreate, PaymentStatus.PENDING));

        try {
            paymentProcessorClient.createPayment(paymentToCreate);
            payment.setStatus(PaymentStatus.COMPLETE);
        } catch (FeignException ex) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setStatusMessage(ex.getMessage());
            log.error(
                "Call to payment processor for new payment failed. Status: {}, request: {}",
                ex.status(),
                ex.request()
            );
        }
        paymentsRepository.save(payment);
    }

    /**
     * Creates an update payment in the database, sends it to payment processor.
     *
     * @param envelopeId The ID of the envelope containing payments.
     * @param jurisdiction The jurisdiction relating to the payment.
     * @param exceptionRecordRef The original exception record reference for the envelope.
     * @param newCaseRef The new case reference to move the payment over to.
     */
    public void updatePayment(String envelopeId, String jurisdiction, String exceptionRecordRef, String newCaseRef) {
        UpdatePaymentDTO paymentToUpdate = new UpdatePaymentDTO(
            envelopeId,
            jurisdiction,
            exceptionRecordRef,
            newCaseRef
        );

        UpdatePayment updatePayment = updatePaymentsRepository.save(new UpdatePayment(
            paymentToUpdate,
            PaymentStatus.PENDING
        ));

        try {
            paymentProcessorClient.updatePayment(paymentToUpdate); //TODO calls here
            updatePayment.setStatus(PaymentStatus.COMPLETE);
        } catch (FeignException ex) {
            updatePayment.setStatus(PaymentStatus.FAILED);
            updatePayment.setStatusMessage(ex.getMessage());
            log.error(
                "Call to payment processor for update payment failed. Status: {}, request: {}",
                ex.status(),
                ex.request()
            );
        }

        updatePaymentsRepository.save(updatePayment);
    }

    /**
     * Returns all new payments that have failed processing.
     *
     * @return A list of failed new payments.
     */
    public List<Payment> getAllFailedNewPayments() {
        return paymentsRepository.findAllByStatus(PaymentStatus.FAILED);
    }

    /**
     * Returns all update payments that have failed processing.
     *
     * @return A list of failed update payments.
     */
    public List<UpdatePayment> getAllFailedUpdatePayments() {
        return updatePaymentsRepository.findAllByStatus(PaymentStatus.FAILED);
    }

    /**
     * Reprocess a requested payment.
     *
     * @param paymentId The ID of the payment to reprocess.
     * @return The payment that has been reprocessed.
     */
    public Payment reprocessNewPayment(String paymentId) {
        Payment paymentToReprocess = paymentsRepository.findById(UUID.fromString(paymentId)).orElseThrow(() ->
            new NotFoundException(String.format("Payment with id '%s' not found", paymentId)));

        try {
            paymentProcessorClient.createPayment(new CreatePaymentDTO(paymentToReprocess));
            paymentToReprocess.setStatus(PaymentStatus.COMPLETE);
            paymentToReprocess.setStatusMessage("");
        } catch (FeignException ex) {
            paymentToReprocess.setStatusMessage(ex.getMessage());
            paymentsRepository.save(paymentToReprocess);

            throw new PaymentReprocessFailedException(ex.getMessage());
        }

        return paymentsRepository.save(paymentToReprocess);
    }

    /**
     * Reprocess a requested payment.
     *
     * @param paymentId The ID of the payment to reprocess.
     * @return The payment that has been reprocessed.
     */
    public UpdatePayment reprocessUpdatePayment(String paymentId) {
        UpdatePayment paymentToReprocess = updatePaymentsRepository.findById(
            UUID.fromString(paymentId)).orElseThrow(() ->
            new NotFoundException(String.format("Payment with id '%s' not found", paymentId)));

        try {
            paymentProcessorClient.updatePayment(new UpdatePaymentDTO(paymentToReprocess)); //TODO calls here
            paymentToReprocess.setStatus(PaymentStatus.COMPLETE);
            paymentToReprocess.setStatusMessage("");
        } catch (FeignException ex) {
            paymentToReprocess.setStatusMessage(ex.getMessage());
            updatePaymentsRepository.save(paymentToReprocess);

            throw new PaymentReprocessFailedException(ex.getMessage());
        }

        return updatePaymentsRepository.save(paymentToReprocess);
    }
}
