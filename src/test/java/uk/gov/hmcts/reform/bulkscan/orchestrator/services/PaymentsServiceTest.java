package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.internal.PaymentProcessorClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.database.PaymentsRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.database.UpdatePaymentsRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.NotFoundException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.PaymentReprocessFailedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.CreatePaymentDTO;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.PaymentStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.UpdatePaymentDTO;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentsServiceTest {

    @Mock
    private PaymentProcessorClient paymentProcessorClient;

    @Mock
    private PaymentsRepository paymentsRepository;

    @Mock
    private UpdatePaymentsRepository updatePaymentsRepository;

    private PaymentsService paymentsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentsService = new PaymentsService(paymentProcessorClient,
            paymentsRepository, updatePaymentsRepository);
    }

    @Test
    void shouldCreateNewPaymentAndSendToPaymentProcessor() {

        Payment savedPayment = new Payment();
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setStatus(PaymentStatus.PENDING);

        when(paymentsRepository.save(any(Payment.class))).thenReturn(savedPayment);

        Envelope envelope = new Envelope(
            UUID.randomUUID().toString(),
            "caseRef123",
            null,
            "poBox123",
            "jurisdiction1",
            "container1",
            "ok.zip",
            null,
            Instant.now(),
            Instant.now(),
            Classification.NEW_APPLICATION,
            List.of(),
            List.of(new uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus
                .domains.envelopes.model.Payment("docControlNumber123")),
            List.of(),
            List.of()
        );

        paymentsService.createNewPayment(envelope, false, 0L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentsRepository, times(2)).save(paymentCaptor.capture());
        verify(paymentProcessorClient, times(1)).createPayment(any());

        List<Payment> capturedPayments = paymentCaptor.getAllValues();
        assertThat(capturedPayments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(capturedPayments.get(1).getStatus()).isEqualTo(PaymentStatus.COMPLETE);
    }

    @Test
    void shouldUpdatePaymentAndSendToPaymentProcessor() {

        UpdatePayment updatePayment = new UpdatePayment();
        updatePayment.setId(UUID.randomUUID());
        updatePayment.setStatus(PaymentStatus.PENDING);

        when(updatePaymentsRepository.save(any(UpdatePayment.class))).thenReturn(updatePayment);

        String envelopeId = UUID.randomUUID().toString();
        String jurisdiction = "jurisdiction1";
        String exceptionRecordRef = "exceptionRecord123";
        String newCaseRef = "newCaseRef123";

        paymentsService.updatePayment(envelopeId, jurisdiction, exceptionRecordRef, newCaseRef);

        ArgumentCaptor<UpdatePayment> updatePaymentCaptor = ArgumentCaptor.forClass(UpdatePayment.class);
        verify(updatePaymentsRepository, times(2)).save(updatePaymentCaptor.capture());
        verify(paymentProcessorClient, times(1)).updatePayment(any());

        List<UpdatePayment> capturedPayments = updatePaymentCaptor.getAllValues();
        assertThat(capturedPayments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(capturedPayments.get(1).getStatus()).isEqualTo(PaymentStatus.COMPLETE);
    }

    @Test
    void shouldReturnAllFailedNewPayments() {
        List<Payment> failedPayments = List.of(new Payment(), new Payment());
        when(paymentsRepository.findAllByStatus(PaymentStatus.FAILED)).thenReturn(failedPayments);

        List<Payment> result = paymentsService.getAllFailedNewPayments();

        assertThat(result).isEqualTo(failedPayments);
        verify(paymentsRepository, times(1)).findAllByStatus(PaymentStatus.FAILED);
    }

    @Test
    void shouldReturnAllFailedUpdatePayments() {
        List<UpdatePayment> failedUpdatePayments = List.of(new UpdatePayment(), new UpdatePayment());
        when(updatePaymentsRepository.findAllByStatus(PaymentStatus.FAILED)).thenReturn(failedUpdatePayments);

        List<UpdatePayment> result = paymentsService.getAllFailedUpdatePayments();

        assertThat(result).isEqualTo(failedUpdatePayments);
        verify(updatePaymentsRepository, times(1)).findAllByStatus(PaymentStatus.FAILED);
    }

    @Test
    void shouldReprocessNewPaymentSuccessfully() {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = new Payment();
        payment.setId(UUID.fromString(paymentId));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setPayments(List.of("docControlNumber123"));

        when(paymentsRepository.findById(UUID.fromString(paymentId))).thenReturn(java.util.Optional.of(payment));

        paymentsService.reprocessNewPayment(paymentId);

        verify(paymentsRepository, times(1)).save(payment);
        verify(paymentProcessorClient, times(1)).createPayment(any(CreatePaymentDTO.class));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETE);
    }

    @Test
    void shouldReprocessUpdatePaymentSuccessfully() {
        String paymentId = UUID.randomUUID().toString();
        UpdatePayment updatePayment = new UpdatePayment();
        updatePayment.setId(UUID.fromString(paymentId));
        updatePayment.setStatus(PaymentStatus.FAILED);

        when(updatePaymentsRepository.findById(UUID.fromString(paymentId)))
            .thenReturn(java.util.Optional.of(updatePayment));

        paymentsService.reprocessUpdatePayment(paymentId);

        verify(paymentProcessorClient, times(1)).updatePayment(any(UpdatePaymentDTO.class));
        verify(updatePaymentsRepository, times(1)).save(updatePayment);
        assertThat(updatePayment.getStatus()).isEqualTo(PaymentStatus.COMPLETE);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenReprocessingNewPaymentAndPaymentDoesNotExist() {
        String paymentId = UUID.randomUUID().toString();

        when(paymentsRepository.findById(UUID.fromString(paymentId))).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> paymentsService.reprocessNewPayment(paymentId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Payment with id '" + paymentId + "' not found");

        verify(paymentsRepository, times(1)).findById(UUID.fromString(paymentId));
        verifyNoInteractions(paymentProcessorClient);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenReprocessingUpdatePaymentAndPaymentDoesNotExist() {
        String paymentId = UUID.randomUUID().toString();

        when(updatePaymentsRepository.findById(UUID.fromString(paymentId))).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> paymentsService.reprocessUpdatePayment(paymentId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Payment with id '" + paymentId + "' not found");

        verify(updatePaymentsRepository, times(1)).findById(UUID.fromString(paymentId));
        verifyNoInteractions(paymentProcessorClient);
    }

    @Test
    void shouldSetPaymentStatusToFailedWhenFeignExceptionIsThrown() {
        Payment savedPayment = new Payment();
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setStatus(PaymentStatus.PENDING);

        when(paymentsRepository.save(any(Payment.class))).thenReturn(savedPayment);
        doThrow(FeignException.class).when(paymentProcessorClient).createPayment(any());

        Envelope envelope = new Envelope(
            UUID.randomUUID().toString(),
            "caseRef123",
            null,
            "poBox123",
            "jurisdiction1",
            "container1",
            "ok.zip",
            null,
            Instant.now(),
            Instant.now(),
            Classification.NEW_APPLICATION,
            List.of(),
            List.of(new uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus
                .domains.envelopes.model.Payment("docControlNumber123")),
            List.of(),
            List.of()
        );

        paymentsService.createNewPayment(envelope, false, 0L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentsRepository, times(2)).save(paymentCaptor.capture());
        verify(paymentProcessorClient, times(1)).createPayment(any());

        List<Payment> capturedPayments = paymentCaptor.getAllValues();
        assertThat(capturedPayments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(capturedPayments.get(1).getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldSetUpdatePaymentStatusToFailedWhenFeignExceptionIsThrown() {
        UpdatePayment updatePayment = new UpdatePayment();
        updatePayment.setId(UUID.randomUUID());
        updatePayment.setStatus(PaymentStatus.PENDING);

        when(updatePaymentsRepository.save(any(UpdatePayment.class))).thenReturn(updatePayment);
        doThrow(FeignException.class).when(paymentProcessorClient).updatePayment(any());

        String envelopeId = UUID.randomUUID().toString();
        String jurisdiction = "jurisdiction1";
        String exceptionRecordRef = "exceptionRecord123";
        String newCaseRef = "newCaseRef123";

        paymentsService.updatePayment(envelopeId, jurisdiction, exceptionRecordRef, newCaseRef);

        ArgumentCaptor<UpdatePayment> updatePaymentCaptor = ArgumentCaptor.forClass(UpdatePayment.class);
        verify(updatePaymentsRepository, times(2)).save(updatePaymentCaptor.capture());
        verify(paymentProcessorClient, times(1)).updatePayment(any());

        List<UpdatePayment> capturedPayments = updatePaymentCaptor.getAllValues();
        assertThat(capturedPayments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(capturedPayments.get(1).getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldUpdateDatabaseAndThrowPaymentReprocessFailedExceptionWhenFeignExceptionIsThrownNew() {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = new Payment();
        payment.setId(UUID.fromString(paymentId));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setPayments(List.of("docControlNumber123"));

        when(paymentsRepository.findById(UUID.fromString(paymentId))).thenReturn(java.util.Optional.of(payment));
        doThrow(FeignException.class).when(paymentProcessorClient).createPayment(any(CreatePaymentDTO.class));

        assertThatThrownBy(() -> paymentsService.reprocessNewPayment(paymentId))
            .isInstanceOf(PaymentReprocessFailedException.class);

        verify(paymentsRepository, times(1)).save(payment);
        verify(paymentProcessorClient, times(1)).createPayment(any(CreatePaymentDTO.class));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldUpdateDatabaseAndThrowPaymentReprocessFailedExceptionWhenFeignExceptionIsThrownUpdate() {
        String paymentId = UUID.randomUUID().toString();
        UpdatePayment updatePayment = new UpdatePayment();
        updatePayment.setId(UUID.fromString(paymentId));
        updatePayment.setStatus(PaymentStatus.FAILED);

        when(updatePaymentsRepository.findById(UUID.fromString(paymentId)))
            .thenReturn(java.util.Optional.of(updatePayment));
        doThrow(FeignException.class).when(paymentProcessorClient).updatePayment(any(UpdatePaymentDTO.class));

        assertThatThrownBy(() -> paymentsService.reprocessUpdatePayment(paymentId))
            .isInstanceOf(PaymentReprocessFailedException.class);

        verify(updatePaymentsRepository, times(1)).save(updatePayment);
        verify(paymentProcessorClient, times(1)).updatePayment(any(UpdatePaymentDTO.class));
        assertThat(updatePayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
