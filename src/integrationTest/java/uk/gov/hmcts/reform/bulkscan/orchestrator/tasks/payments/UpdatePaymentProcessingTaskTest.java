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
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.UpdatePaymentService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment.UpdatePaymentProcessingTask;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class UpdatePaymentProcessingTaskTest {

    @Autowired
    private UpdatePaymentService updatePaymentService;

    @Autowired
    private UpdatePaymentRepository updatePaymentRepository;

    @Autowired
    PaymentApiClient paymentApiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UpdatePaymentProcessingTask updatePaymentProcessingTask;

    final UpdatePayment updatePayment = new UpdatePayment(
        Instant.now(),
        "exceptionRecordRef",
        "newCaseRef",
        "1234pass",
        "jurisdiction",
        "awaiting"
    );

    final UpdatePayment updatePayment2 = new UpdatePayment(
        Instant.now(),
        "exceptionRecordRef",
        "newCaseRef",
        "1234pass",
        "jurisdiction",
        "awaiting"
    );

    @BeforeEach
    void setUp() {
        updatePaymentService = new UpdatePaymentService(updatePaymentRepository);
        updatePaymentProcessingTask =
            new UpdatePaymentProcessingTask(updatePaymentService, paymentApiClient, 3);
    }

    @AfterEach
    void tearDown() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "update_payments");
    }

    @Test
    @Transactional
    public void should_process_payments_from_database_that_have_awaiting_status() {
        updatePaymentService.savePayment(updatePayment);
        updatePaymentService.savePayment(updatePayment2);

        assertThat(updatePaymentService.getUpdatePaymentByStatus("awaiting")).hasSize(2);

        updatePaymentProcessingTask.processUpdatePayments();

        assertThat(updatePaymentService.getUpdatePaymentByStatus("awaiting")).hasSize(0);
        assertThat(updatePaymentService.getUpdatePaymentByStatus("success")).hasSize(2);
        assertThat(updatePaymentService.getUpdatePaymentByStatus("error")).hasSize(0);
    }

    @Test
    public void should_not_update_payments_if_none_in_database_with_status_awaiting() {
        final UpdatePayment errorUpdatePayment = new UpdatePayment(
            Instant.now(),
            "exceptionRecordRef",
            "newCaseRef",
            "envelope_id",
            "jurisdiction",
            "error"
        );

        updatePaymentService.savePayment(errorUpdatePayment);

        assertThat(updatePaymentService.getUpdatePaymentByStatus("awaiting")).hasSize(0);

        updatePaymentProcessingTask.processUpdatePayments();

        assertThat(updatePaymentService.getUpdatePaymentByStatus("awaiting")).hasSize(0);
        assertThat(updatePaymentService.getUpdatePaymentByStatus("success")).hasSize(0);
        assertThat(updatePaymentService.getUpdatePaymentByStatus("error")).hasSize(1);
    }

    @Transactional
    @Test
    public void should_save_update_payment_with_error_status_when_call_to_payment_api_fails() {
        final UpdatePayment errorUpdatePayment = new UpdatePayment(
            Instant.now(),
            "exceptionRecordRef",
            "newCaseRef",
            "1234fail",
            "jurisdiction",
            "awaiting"
        );

        updatePaymentService.savePayment(errorUpdatePayment);

        assertThat(updatePaymentService.getUpdatePaymentByStatus("awaiting")).hasSize(1);

        updatePaymentProcessingTask.processUpdatePayments();

        assertThat(updatePaymentService.getUpdatePaymentByStatus("awaiting")).hasSize(0);
        assertThat(updatePaymentService.getUpdatePaymentByStatus("success")).hasSize(0);
        assertThat(updatePaymentService.getUpdatePaymentByStatus("error")).hasSize(1);
    }
}
