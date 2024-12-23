package uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@ExtendWith(MockitoExtension.class)
public class PaymentApiClientTest {


    @Mock
    private RestTemplate restTemplate;


    private static final PaymentData paymentData = new PaymentData(Instant.now(),"123");

    private static final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment paymentEntity1 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment(
            Instant.now(),
            "123",
            "234",
            "jurisdiction1",
            "service1",
            "poBox1",
            "awaiting",
            true,
            Collections.singletonList(paymentData)
        );

    private static final  uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment paymentEntity2 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment(
            Instant.now(),
            "456",
            "789",
            "jurisdiction2",
            "service2",
            "poBox2",
            "awaiting",
            true,
            Collections.singletonList(paymentData)
        );

    private static final uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment payment1 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment(paymentEntity1);

    private static final uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment payment2 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment(paymentEntity2);

    private String url;
    private PaymentApiClient paymentApiClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        this.url = "http://localhost:8583/payment/create";
        this.paymentApiClient = new PaymentApiClient(restTemplate, url);
    }

    @Test
    void postForEntity() {

        // response entity
        ResponseEntity<String> response = ResponseEntity.ok("RESPONSE");

        // mockito mock
        Mockito.when(restTemplate.postForEntity(
            ArgumentMatchers.eq(url),
            any(HttpHeaders.class),
            ArgumentMatchers.eq(String.class)
        )).thenReturn(response);


        List<Payment> paymentList = Arrays.asList(payment1, payment2);
        // to check the results
//        Map<String, String> requestBody = new HashMap<>();
//        requestBody.put("type", "a,b,c,d");
//        requestBody.put("data", "aws");
//        JSONObject jsonObject = new JSONObject(requestBody);
//        HttpEntity<String> request = new HttpEntity<>(jsonObject.toString(), null);




        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", APPLICATION_JSON.toString());


        // actual call
        paymentApiClient.postPayment(payment1);

        // verification
        Mockito.verify(restTemplate, times(1)).postForEntity(url,any(),any());
    }


}
