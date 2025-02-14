package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class PaymentServiceTest {

    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentRepository paymentRepository;


    final PaymentData paymentData = new PaymentData("123");
    final Payment payment = new Payment(
        "envelope_id",
        Instant.now(),
        "ccdReference",
        "jurisdiction",
        "service",
        "poBox",
        true,
        "awaiting",
        Collections.singletonList(paymentData)
    );

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository);
    }

    @Test
    void shouldSaveAndThenGetPaymentFromDatabaseByStatus() {

        paymentService.savePayment(payment);

        List<Payment> results =
            paymentService.getPaymentsByStatus("awaiting");

        assertThat(results.getFirst().status).isEqualTo("awaiting");
    }

}
