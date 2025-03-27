package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class UpdatePaymentServiceTest {

    @Autowired
    UpdatePaymentService updatePaymentService;

    @Autowired
    UpdatePaymentRepository updatePaymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    final UpdatePayment updatePayment = new UpdatePayment(
        Instant.now(),
        "exceptionRecordRef",
        "newCaseRef",
        "envelope_id",
        "jurisdiction",
        "awaiting"
    );

    @BeforeEach
    void setUp() {
        updatePaymentService = new UpdatePaymentService(updatePaymentRepository);
    }

    @AfterEach
    void tearDown() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "update_payments");
    }

    @Test
    void shouldSaveAndThenGetUpdatePaymentFromDatabaseByStatus() {

        updatePaymentService.savePayment(updatePayment);

        List<UpdatePayment> results =
            updatePaymentService.getUpdatePaymentByStatus("awaiting");

        assertThat(results.getFirst().status).isEqualTo("awaiting");
    }

    @Test
    void shouldNotRetrievePaymentUpdatesCreatedAfterDateGiven() {
        Instant createdAt1 = LocalDateTime.of(2025, 3, 21, 23, 58, 59, 0).toInstant(ZoneOffset.UTC);
        final UpdatePayment payment1 = new UpdatePayment(
            createdAt1,
            "ccdReference",
            "newCaseRef",
            "envelopeId",
            "service",
            "awaiting"
        );
        updatePaymentService.savePayment(payment1);

        System.out.println(payment1.createdAt.toString());

        Instant createdAt2 = LocalDateTime.of(2025, 3, 21, 1, 0, 0, 0).toInstant(ZoneOffset.UTC);
        final UpdatePayment payment2 = new UpdatePayment(
            createdAt2,
            "ccdReference",
            "newCaseRef",
            "envelopeId",
            "service",
            "awaiting"
        );
        updatePaymentService.savePayment(payment2);

        System.out.println(payment2.createdAt.toString());

        Instant createdAt3 = LocalDateTime.of(2025, 3, 20, 23, 59, 59, 0).toInstant(ZoneOffset.UTC);
        final UpdatePayment payment3 = new UpdatePayment(
            createdAt3,
            "ccdReference",
            "newCaseRef",
            "envelopeId",
            "service",
            "awaiting"
        );
        updatePaymentService.savePayment(payment3);

        System.out.println(payment3.createdAt.toString());

        List<UpdatePayment> paymentsFromDb =
            updatePaymentService.getAllByPaymentsByDate(LocalDate.of(2025, 3, 21));

        assertThat(paymentsFromDb)
            .hasSize(2);
    }

    @Test
    void shouldNotRetrievePaymentUpdatesCreatedBeforeDateGiven() {
        Instant createdAt1 = LocalDateTime.of(2025, 3, 21, 23, 58, 59, 0).toInstant(ZoneOffset.UTC);
        final UpdatePayment payment1 = new UpdatePayment(
            createdAt1,
            "ccdReference",
            "newCaseRef",
            "envelopeId",
            "service",
            "awaiting"
        );
        updatePaymentService.savePayment(payment1);

        System.out.println(payment1.createdAt.toString());

        Instant createdAt2 = LocalDateTime.of(2025, 3, 21, 1, 0, 0, 0).toInstant(ZoneOffset.UTC);
        final UpdatePayment payment2 = new UpdatePayment(
            createdAt2,
            "ccdReference",
            "newCaseRef",
            "envelopeId",
            "service",
            "awaiting"
        );
        updatePaymentService.savePayment(payment2);

        System.out.println(payment2.createdAt.toString());

        Instant createdAt3 = LocalDateTime.of(2025, 3, 22, 0, 0, 1, 0).toInstant(ZoneOffset.UTC);
        final UpdatePayment payment3 = new UpdatePayment(
            createdAt3,
            "ccdReference",
            "newCaseRef",
            "envelopeId",
            "service",
            "awaiting"
        );
        updatePaymentService.savePayment(payment3);

        System.out.println(payment3.createdAt.toString());

        List<UpdatePayment> paymentsFromDb =
            updatePaymentService.getAllByPaymentsByDate(LocalDate.of(2025, 3, 21));

        assertThat(paymentsFromDb)
            .hasSize(2);
    }

}
