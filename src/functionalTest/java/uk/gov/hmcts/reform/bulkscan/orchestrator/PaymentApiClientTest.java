package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ExceptionRecordCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.JmsEnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest()
@ActiveProfiles("nosb")
@TestPropertySource(locations = "classpath:application.yaml")
@Import(FunctionalQueueConfig.class)
class PaymentApiClientTest {

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private JmsEnvelopeMessager jmsEnvelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    @Autowired
    private PaymentApiClient paymentApiClient;

    @Autowired
    private ExceptionRecordCreator exceptionRecordCreator;

    String dmUrl;
    String documentUuid;

    private Payment testPayment = new Payment(
        "137436bd-ed50-460c-b6c8-f7205528a5a9",
        Instant.now(),
//        "1539860706648396",
        "2222222222222222",
        "BULKSCAN",
        "bulkscan",
        "BULKSCANPO",
        false,
        Status.AWAITING.toString(),
        List.of(new PaymentData(
            "11111111111111111111"))
    );

    private UpdatePayment testUpdatePayment = new UpdatePayment(
        Instant.now(),
        "1111222233334444",
        "3454645678909876",
        "137436bd-ed50-460c-b6c8-f7205528a5a9",
        "BULKSCAN",
        Status.SUCCESS.toString()
    );

    @BeforeEach
    void setup() {

    }

    @Test
    void shouldPostPaymentSuccessfully() throws Exception {

        ResponseEntity<String> response = paymentApiClient.postPayment(testPayment);

        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("Payment created successfully");
    }

    @Test
    void shouldNotPostDataSuccessfullyIfDocumentControlNumberIsInvalid() throws Exception {

        Payment myDocumentControlNumberIsTooShort = new Payment(
            "137436bd-ed50-460c-b6c8-f7205528a5a9",
            Instant.now(),
            "2222222222222222",
            "BULKSCAN",
            "bulkscan",
            "BULKSCANPO",
            false,
            Status.AWAITING.toString(),
            List.of(new PaymentData(
                "111111111111111111"))
        );

        assertThatThrownBy(() -> paymentApiClient.postPayment(myDocumentControlNumberIsTooShort))
            .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void shouldPostUpdatePaymentSuccessfully() {

        // create exception record one (refer to ExceptionRecordCreationTest tests for this)

        // create exception record two (in reality it would be a real case)
        // alternatively create a real case for this which is more realistic

        // call endpoint update to say "assign payments from creation record 1 to 2"

        // verify response 200 or w/e it is
        ResponseEntity<String> response = paymentApiClient.postUpdatePayment(testUpdatePayment);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Payment updated successfully");
    }
}
