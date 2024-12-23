package uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData;

import java.time.Instant;
import java.util.Collections;

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




}
