package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class PaymentServiceTest {

    @Autowired
    PaymentService paymentService;

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

    @Test
    void shouldAddPaymentToDatabase() {

        paymentService.savePayment(payment);

        assertThatCode(() -> paymentService.savePayment(payment))
            .doesNotThrowAnyException();

    }

    @Test
    void shouldGetPaymentFromDatabaseByStatus() {

        paymentService.savePayment(payment);

        assertThat(paymentService.getPaymentsByStatus("awaiting")).size().isEqualTo(1);
    }


}
