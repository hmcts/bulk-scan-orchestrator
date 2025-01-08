package uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class PaymentApiClientTest {

    @Mock
    private RestTemplate restTemplate;


    private static final PaymentData paymentData = new PaymentData("123");

    private static final Payment payment = new Payment(
            "123",
            Instant.now(),
            "234",
            "jurisdiction1",
            "service1",
            "poBox1",
            true,
            "awaiting",
            Collections.singletonList(paymentData)
        );

    private static final UpdatePayment updatePayment = new UpdatePayment(
        Instant.now(),
        "12345",
        "34567",
        "6789432",
        "jurisidiction",
        "awaiting"
    );

    private String url;
    private PaymentApiClient paymentApiClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        this.url = "http://localhost:8583/payment/create";
        this.paymentApiClient = new PaymentApiClient(restTemplate, url);
    }


    @Test
    void should_post_create_payments_successfully() throws Exception {

        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .willReturn(new ResponseEntity<String>("RESULT", HttpStatus.OK));

        ResponseEntity<String> result = paymentApiClient.postPayment(payment);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_post_update_payments_successfully() throws Exception {

        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .willReturn(new ResponseEntity<String>("RESULT", HttpStatus.OK));
        ResponseEntity<String> result = paymentApiClient.postUpdatePayment(updatePayment);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);

    }

}
