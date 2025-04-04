package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.PaymentService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment.PaymentProcessingTask;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class PaymentProcessingTaskTest {

    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentApiClient paymentApiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    PaymentProcessingTask paymentProcessingTask;

    final PaymentData paymentData = new PaymentData("123");
    final Payment payment = new Payment(
        "1234pass",
        Instant.now(),
        "ccdReference",
        "jurisdiction",
        "service",
        "poBox",
        true,
        "awaiting",
        Collections.singletonList(paymentData)
    );

    final PaymentData paymentData2 = new PaymentData("123");
    final Payment payment2 = new Payment(
        "1234pass",
        Instant.now(),
        "ccdReference",
        "jurisdiction",
        "service",
        "poBox",
        true,
        "awaiting",
        Collections.singletonList(paymentData2)
    );

    @BeforeEach
    public void setUp() {
        paymentService = new PaymentService(paymentRepository);
        paymentProcessingTask = new PaymentProcessingTask(paymentService, paymentApiClient, 3);
    }

    @AfterEach
    void tearDown() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "payments_data", "payments");
    }

    @Test
    @Transactional
    public void should_process_payments_from_database_that_have_awaiting_status() {
        paymentService.savePayment(payment);
        paymentService.savePayment(payment2);

        assertThat(paymentService.getPaymentsByStatus("awaiting")).hasSize(2);

        paymentProcessingTask.processPayments();

        assertThat(paymentService.getPaymentsByStatus("awaiting")).hasSize(0);
        assertThat(paymentService.getPaymentsByStatus("success")).hasSize(2);
        assertThat(paymentService.getPaymentsByStatus("error")).hasSize(0);
    }

    @Test
    public void should_not_process_payments_if_no_payments() {
        paymentProcessingTask.processPayments();

        assertThat(paymentService.getPaymentsByStatus("awaiting")).hasSize(0);
        assertThat(paymentService.getPaymentsByStatus("success")).hasSize(0);
        assertThat(paymentService.getPaymentsByStatus("error")).hasSize(0);
    }

    @Test
    @Transactional
    public void should_update_status_of_payments_to_error_when_call_to_payment_api_fails() {
        final PaymentData paymentData = new PaymentData("123");
        final Payment paymentFail = new Payment(
            "1234fail",
            Instant.now(),
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            Collections.singletonList(paymentData)
        );

        assertThat(paymentService.getPaymentsByStatus("awaiting")).hasSize(0);

        paymentService.savePayment(paymentFail);

        paymentProcessingTask.processPayments();

        assertThat(paymentService.getPaymentsByStatus("awaiting")).hasSize(0);
        assertThat(paymentService.getPaymentsByStatus("success")).hasSize(0);
        assertThat(paymentService.getPaymentsByStatus("error")).hasSize(1);
    }
}
