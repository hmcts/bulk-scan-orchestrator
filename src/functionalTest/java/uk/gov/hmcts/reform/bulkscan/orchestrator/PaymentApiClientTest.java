package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ExceptionRecordCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest()
@ActiveProfiles("nosb")
@TestPropertySource(locations = "classpath:application.yaml")
@Import(FunctionalQueueConfig.class)
class PaymentApiClientTest {

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    @Autowired
    private PaymentApiClient paymentApiClient;

    @Autowired
    private ExceptionRecordCreator exceptionRecordCreator;

    @Autowired
    CcdCaseCreator ccdCaseCreator;

    String dmUrl;

    private static final int CCD_EIGHT_DIGIT_UPPER = 99_999_999;
    private static final int CCD_EIGHT_DIGIT_LOWER = 10_000_000;

    @BeforeEach
    void setup() {
        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @Test
    void shouldPostPaymentSuccessfully() throws Exception {
        Payment testPayment = new Payment(
            "137436bd-ed50-460c-b6c8-f7205528a5a9",
            Instant.now(),
            "2222222222222222",
            "BULKSCAN",
            "bulkscan",
            "BULKSCANPO",
            false,
            Status.AWAITING.toString(),
            List.of(new PaymentData(
                "672329182343485934323"))
        );

        ResponseEntity<String> response = paymentApiClient.postPayment(testPayment);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
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
    void shouldPostUpdatePaymentSuccessfully() throws Exception {
        //create a random dcn number in style of ccpay-bulkscanning-app
        String dcn = "6200000000001" + RandomUtils.nextInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        // create exception record
        CaseDetails exceptionRecord = exceptionRecordCreator.createExceptionRecord(
            SampleData.CONTAINER,
            "envelopes/exception-classification-envelope-with-payment.json",
            dmUrl
        );

        // create new case
        CaseDetails newCase = ccdCaseCreator.createCase(emptyList(), Instant.now());

        //create payment for exception record
        Payment testPayment = new Payment(
            "137436bd-ed50-460c-b6c8-f7205528a5a9",
            Instant.now(),
            exceptionRecord.getId().toString(),
            "BULKSCAN",
            "bulkscan",
            "BULKSCANPO",
            true,
            Status.AWAITING.toString(),
            List.of(new PaymentData(dcn))
        );
        paymentApiClient.postPayment(testPayment);

        // call endpoint update to say "assign payments from exception record to new case"
        UpdatePayment testUpdatePayment = new UpdatePayment(
            Instant.now(),
            exceptionRecord.getId().toString(),
            newCase.getId().toString(),
            "137436bd-ed50-460c-b6c8-f7205528a5a9",
            "BULKSCAN",
            Status.SUCCESS.toString()
        );

        // verify response 200
        ResponseEntity<String> response = paymentApiClient.postUpdatePayment(testUpdatePayment);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Payment updated successfully");
    }
}
