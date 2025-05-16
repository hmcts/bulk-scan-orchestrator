package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.NotFoundException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.PaymentReprocessFailedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.PaymentStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentsController.class)
class PaymentsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentsService paymentsService;

    @Test
    void should_return_failed_new_payments() throws Exception {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(id);
        payment.setEnvelopeId("env123");
        payment.setCcdReference("ccd123");
        payment.setExceptionRecord(true);
        payment.setPoBox("PO123");
        payment.setJurisdiction("IA");
        payment.setService("BULKSCAN");
        payment.setPayments(List.of("DCN12345"));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setStatusMessage("Failure reason");

        given(paymentsService.getAllFailedNewPayments()).willReturn(List.of(payment));

        mvc.perform(get("/payments/new/failed"))
            .andExpect(status().isOk())
            .andExpect(content().json(
                "[{"
                    + "'id':'" + id + "',"
                    + "'envelopeId':'env123',"
                    + "'ccdReference':'ccd123',"
                    + "'exceptionRecord':true,"
                    + "'poBox':'PO123',"
                    + "'jurisdiction':'IA',"
                    + "'service':'BULKSCAN',"
                    + "'payments':['DCN12345'],"
                    + "'status':'FAILED',"
                    + "'statusMessage':'Failure reason'"
                    + "}]"
            ));
    }

    @Test
    void should_return_failed_new_payments_when_none() throws Exception {

        given(paymentsService.getAllFailedNewPayments()).willReturn(List.of());

        mvc.perform(get("/payments/new/failed"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void should_return_failed_updated_payments() throws Exception {
        UUID id = UUID.randomUUID();
        UpdatePayment payment = new UpdatePayment();
        payment.setId(id);
        payment.setEnvelopeId("env456");
        payment.setJurisdiction("IA");
        payment.setExceptionRecordRef("ER123");
        payment.setNewCaseRef("CASE456");
        payment.setStatus(PaymentStatus.FAILED);
        payment.setStatusMessage("Update failed");

        given(paymentsService.getAllFailedUpdatePayments()).willReturn(List.of(payment));

        mvc.perform(get("/payments/updated/failed"))
            .andExpect(status().isOk())
            .andExpect(content().json(
                "[{"
                    + "'id':'" + id + "',"
                    + "'envelopeId':'env456',"
                    + "'jurisdiction':'IA',"
                    + "'exceptionRecordRef':'ER123',"
                    + "'newCaseRef':'CASE456',"
                    + "'status':'FAILED',"
                    + "'statusMessage':'Update failed'"
                    + "}]"
            ));
    }

    @Test
    void should_return_failed_updated_payments_when_none() throws Exception {

        given(paymentsService.getAllFailedUpdatePayments()).willReturn(List.of());

        mvc.perform(get("/payments/updated/failed"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void should_retry_failed_new_payment_successfully() throws Exception {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(id);
        payment.setEnvelopeId("env999");
        payment.setCcdReference("ccd999");
        payment.setExceptionRecord(false);
        payment.setPoBox("POBOX9");
        payment.setJurisdiction("CMC");
        payment.setService("BULKSCAN");
        payment.setPayments(List.of("DCN999"));
        payment.setStatus(PaymentStatus.COMPLETE);
        payment.setStatusMessage("Retry successful");

        given(paymentsService.reprocessNewPayment(id.toString())).willReturn(payment);

        mvc.perform(put("/payments/new/retry/" + id))
            .andExpect(status().isOk())
            .andExpect(content().json(
                "{"
                    + "'id':'" + id + "',"
                    + "'envelopeId':'env999',"
                    + "'ccdReference':'ccd999',"
                    + "'exceptionRecord':false,"
                    + "'poBox':'POBOX9',"
                    + "'jurisdiction':'CMC',"
                    + "'service':'BULKSCAN',"
                    + "'payments':['DCN999'],"
                    + "'status':'COMPLETE',"
                    + "'statusMessage':'Retry successful'"
                    + "}"
            ));
    }

    @Test
    void should_retry_failed_updated_payment_successfully() throws Exception {
        UUID id = UUID.randomUUID();
        UpdatePayment payment = new UpdatePayment();
        payment.setId(id);
        payment.setEnvelopeId("env789");
        payment.setJurisdiction("SSCS");
        payment.setExceptionRecordRef("ER999");
        payment.setNewCaseRef("NEWCASE001");
        payment.setStatus(PaymentStatus.COMPLETE);
        payment.setStatusMessage("Retry worked");

        given(paymentsService.reprocessUpdatePayment(id.toString())).willReturn(payment);

        mvc.perform(put("/payments/updated/retry/" + id))
            .andExpect(status().isOk())
            .andExpect(content().json(
                "{"
                    + "'id':'" + id + "',"
                    + "'envelopeId':'env789',"
                    + "'jurisdiction':'SSCS',"
                    + "'exceptionRecordRef':'ER999',"
                    + "'newCaseRef':'NEWCASE001',"
                    + "'status':'COMPLETE',"
                    + "'statusMessage':'Retry worked'"
                    + "}"
            ));
    }

    @Test
    void should_return_404_when_retrying_nonexistent_new_payment() throws Exception {
        UUID id = UUID.randomUUID();

        given(paymentsService.reprocessNewPayment(id.toString()))
            .willThrow(new NotFoundException("Payment not found"));

        mvc.perform(put("/payments/new/retry/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void should_return_422_when_new_payment_retry_fails_processing() throws Exception {
        UUID id = UUID.randomUUID();

        given(paymentsService.reprocessNewPayment(id.toString()))
            .willThrow(new PaymentReprocessFailedException("Processing failed"));

        mvc.perform(put("/payments/new/retry/" + id))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_return_404_when_retrying_nonexistent_updated_payment() throws Exception {
        UUID id = UUID.randomUUID();

        given(paymentsService.reprocessUpdatePayment(id.toString()))
            .willThrow(new NotFoundException("Update payment not found"));

        mvc.perform(put("/payments/updated/retry/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void should_return_422_when_updated_payment_retry_fails_processing() throws Exception {
        UUID id = UUID.randomUUID();

        given(paymentsService.reprocessUpdatePayment(id.toString()))
            .willThrow(new PaymentReprocessFailedException("Retry failed"));

        mvc.perform(put("/payments/updated/retry/" + id))
            .andExpect(status().isUnprocessableEntity());
    }
}

