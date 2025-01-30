package uk.gov.hmcts.reform.bulkscan.orchestrator;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {PaymentApiClient.class, RestTemplate.class})
class PaymentApiClientTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private PaymentApiClient paymentApiClient;

    private Payment testPayment = new Payment(
        "137436bd-ed50-460c-b6c8-f7205528a5a9",
        Instant.now(),
        "CCD-REF-5678",
        "sscs",
        "Bulk Scanning",
        "PO123",
        false,
        Status.AWAITING.toString(),
        List.of(new PaymentData("DCN123456"))
    );

    private UpdatePayment testUpdatePayment = new UpdatePayment(
        Instant.now(),
        "EXC-REF-12345",
        "NEW-CASE-REF-67890",
        "137436bd-ed50-460c-b6c8-f7205528a5a9",
        "sscs",
        Status.SUCCESS.toString()
    );


    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        System.setProperty("payment.api.url", mockWebServer.url("/").toString());
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldPostPaymentSuccessfully() {
        // Mock server response
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("Payment processed successfully"));

        ResponseEntity<String> response = paymentApiClient.postPayment(testPayment);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Payment processed successfully");
    }

    @Test
    void shouldPostUpdatePaymentSuccessfully() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("Payment update successful"));

        ResponseEntity<String> response = paymentApiClient.postUpdatePayment(testUpdatePayment);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Payment update successful");
    }

    @Test
    void shouldReturnErrorForFailedPaymentPost() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        ResponseEntity<String> response = paymentApiClient.postPayment(testPayment);

        assertThat(response.getStatusCodeValue()).isEqualTo(500);
        assertThat(response.getBody()).isEqualTo("Internal Server Error");
    }
}
