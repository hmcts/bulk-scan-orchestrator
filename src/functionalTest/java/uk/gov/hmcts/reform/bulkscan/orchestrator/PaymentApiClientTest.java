package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {PaymentApiClient.class, RestTemplate.class})
@TestPropertySource(locations = "classpath:application.yaml")
class PaymentApiClientTest {

    @Autowired
    private PaymentApiClient paymentApiClient;

    private Payment testPayment = new Payment(
        "137436bd-ed50-460c-b6c8-f7205528a5a9",
        Instant.now(),
        "1539860706648396",
        "BULKSCAN",
        "bulkscan",
        "BULKSCANPO",
        false,
        Status.AWAITING.toString(),
        List.of(new PaymentData("154565768345123456789"))
    );

    private UpdatePayment testUpdatePayment = new UpdatePayment(
        Instant.now(),
        "1111222233334444",
        "3454645678909876",
        "137436bd-ed50-460c-b6c8-f7205528a5a9",
        "BULKSCAN",
        Status.SUCCESS.toString()
    );

    @Test
    void shouldPostPaymentSuccessfully() {
        ResponseEntity<String> response = paymentApiClient.postPayment(testPayment);

        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("Payment created successfully");
    }

    @Test
    void shouldPostUpdatePaymentSuccessfully() {
        ResponseEntity<String> response = paymentApiClient.postUpdatePayment(testUpdatePayment);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Payment updated successfully");
    }
}
