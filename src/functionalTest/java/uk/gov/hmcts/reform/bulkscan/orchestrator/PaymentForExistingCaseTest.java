package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CreateExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getCaseDataForField;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class PaymentForExistingCaseTest {

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private IPaymentsPublisher paymentsPublisher;

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String dmUrl;

    @BeforeEach
    void setup() {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @Test
    public void should_set_awaiting_payment_false_after_payment_sent() throws Exception {
        // given
        UUID randomPoBox = UUID.randomUUID();

        // when
        String messageEnvelopeId = envelopeMessager.sendMessageFromFile(
            "envelopes/new-envelope-with-evidence.json", // with payments dcn
            "0000000000000000",
            null,
            randomPoBox,
            dmUrl
        );

        // then
        await("Exception record should be created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> findCasesByPoBox(randomPoBox).size() == 1);

        CaseDetails caseDetails = findCasesByPoBox(randomPoBox).get(0);

        // envelope ID from the JSON resource representing the test message
        assertThat(caseDetails.getData().get("envelopeId")).isEqualTo(messageEnvelopeId);
        assertThat(getCaseDataForField(caseDetails, "awaitingPaymentDCNProcessing")).isEqualTo("Yes");
        assertThat(getCaseDataForField(caseDetails, "containsPayments")).isEqualTo("Yes");

        //CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList(), Instant.now());

        // given
        //assertThat(caseDetails.getData().get("awaitingPaymentDCNProcessing")).isEqualTo("Yes");

        // when
        // payment sent to payments queue
        paymentsPublisher.send(
            new CreatePaymentsCommand(
                messageEnvelopeId,
                Long.toString(caseDetails.getId()),
                caseDetails.getJurisdiction(),
                "bulkscan",
                randomPoBox.toString(),
                false,
                Arrays.asList(new PaymentData("dcn1"))
            )
        );

        //then
        await("Case is updated")
            .atMost(600, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .until(() -> casePaymentStatusUpdated(caseDetails));
    }

    private Boolean casePaymentStatusUpdated(CaseDetails caseDetails) {
        CaseDetails caseDetailsUpdated =
            ccdApi.getCase(Long.toString(caseDetails.getId()), caseDetails.getJurisdiction());
        return caseDetailsUpdated.getData().get("awaitingPaymentDCNProcessing").equals("No");
    }

    private List<CaseDetails> findCasesByPoBox(UUID poBox) {
        return caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.CONTAINER.toUpperCase() + "_" + CreateExceptionRecord.CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", poBox.toString()
            )
        );
    }
}
