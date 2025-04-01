package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;


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

    @AfterEach
    void tearDown() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "payments_data", "payments");
    }

    @Test
    void shouldSaveAndThenGetPaymentFromDatabaseByStatus() {

        paymentService.savePayment(payment);

        List<Payment> results =
            paymentService.getPaymentsByStatus("awaiting");

        assertThat(results.getFirst().status).isEqualTo("awaiting");
    }

    @Test
    void shouldNotRetrievePaymentsThatDoNotHaveMatchingStatus() {

        paymentService.savePayment(payment);

        List<Payment> results =
            paymentService.getPaymentsByStatus("error");

        assertThat(results).isEmpty();

        results =
            paymentService.getPaymentsByStatus("success");

        assertThat(results).isEmpty();
    }

    @Test
    void shouldRetrievePaymentDataThatAreAssociatedWithPayment() {
        paymentService.savePayment(payment);

        List<Payment> paymentsFromDb =
            paymentService.getPaymentsByStatus("awaiting");

        assertThat(paymentsFromDb)
            .hasSize(1);

        assertThat(paymentsFromDb.getFirst())
            .satisfies(payment -> {
                assertThat(payment.status).isEqualTo("awaiting");
                assertThat(payment.getPayments()).first()
                    .extracting("documentControlNumber")
                    .isEqualTo("123");
            });
    }

    @Test
    void shouldRetrieveMultiplePaymentDataThatAreAssociatedWithPayment() {
        final PaymentData paymentData1 = new PaymentData("123");
        final PaymentData paymentData2 = new PaymentData("456");
        final Payment payment = new Payment(
            "envelope_id",
            Instant.now(),
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            List.of(paymentData1, paymentData2)
        );

        paymentService.savePayment(payment);

        List<Payment> paymentsFromDb =
            paymentService.getPaymentsByStatus("awaiting");

        assertThat(paymentsFromDb)
            .hasSize(1);

        List<PaymentData> paymentDataAssociatedWithPayment = paymentsFromDb.getFirst().getPayments();

        assertThat(paymentDataAssociatedWithPayment)
            .extracting("documentControlNumber")
            .containsExactly("123", "456")
            .doesNotContainNull();
    }

    @Test
    void shouldNotRetrievePaymentDataCreatedAfterDateGiven() {
        Instant createdAt1 = LocalDateTime.of(2025, 3, 21, 23, 58, 59, 0).toInstant(ZoneOffset.UTC);
        final PaymentData paymentData1 = new PaymentData("123");
        final Payment payment1 = new Payment(
            "envelope_id",
            createdAt1,
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            Collections.singletonList(paymentData1)
        );
        paymentService.savePayment(payment1);

        Instant createdAt2 = LocalDateTime.of(2025, 3, 21, 1, 0, 0, 0).toInstant(ZoneOffset.UTC);
        final PaymentData paymentData2 = new PaymentData("123");
        final Payment payment2 = new Payment(
            "envelope_id",
            createdAt2,
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            Collections.singletonList(paymentData2)
        );

        paymentService.savePayment(payment2);

        Instant createdAt3 = LocalDateTime.of(2025, 3, 22, 0, 0, 1, 0).toInstant(ZoneOffset.UTC);
        final PaymentData paymentData3 = new PaymentData("123");
        final Payment payment3 = new Payment(
            "envelope_id",
            createdAt3,
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            Collections.singletonList(paymentData3)
        );

        paymentService.savePayment(payment3);

        List<Payment> paymentsFromDb =
            paymentService.getAllByPaymentsByDate(LocalDate.of(2025, 3, 21));

        assertThat(paymentsFromDb)
            .hasSize(2);
    }

    @Test
    void shouldNotRetrievePaymentDataCreatedBeforeDateGiven() {
        Instant createdAt1 = LocalDateTime.of(2025, 3, 21, 23, 58, 59, 0).toInstant(ZoneOffset.UTC);
        final PaymentData paymentData1 = new PaymentData("123");
        final Payment payment1 = new Payment(
            "envelope_id",
            createdAt1,
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            Collections.singletonList(paymentData1)
        );
        paymentService.savePayment(payment1);

        Instant createdAt2 = LocalDateTime.of(2025, 3, 21, 1, 0, 0, 0).toInstant(ZoneOffset.UTC);
        final PaymentData paymentData2 = new PaymentData("123");
        final Payment payment2 = new Payment(
            "envelope_id",
            createdAt2,
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            Collections.singletonList(paymentData2)
        );

        paymentService.savePayment(payment2);

        Instant createdAt3 = LocalDateTime.of(2025, 3, 20, 23, 59, 59, 0).toInstant(ZoneOffset.UTC);
        final PaymentData paymentData3 = new PaymentData("123");
        final Payment payment3 = new Payment(
            "envelope_id",
            createdAt3,
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            true,
            "awaiting",
            Collections.singletonList(paymentData3)
        );

        paymentService.savePayment(payment3);

        List<Payment> paymentsFromDb =
            paymentService.getAllByPaymentsByDate(LocalDate.of(2025, 3, 21));

        assertThat(paymentsFromDb)
            .hasSize(2);
    }
}
