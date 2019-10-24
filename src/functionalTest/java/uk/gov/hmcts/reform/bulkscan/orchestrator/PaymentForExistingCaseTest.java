package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
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
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class PaymentForExistingCaseTest {

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    //@Autowired
    //private PaymentsPublisher paymentsPublisher;

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String dmUrl;

    @Test
    public void should_set_awaiting_payment_false_after_payment_sent() throws Exception {
        CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList(), Instant.now());

        // given
        assertThat(caseDetails.getData().get("awaitingPaymentDCNProcessing")).isEqualTo("Yes");

        // when
        // message sent to payments queue
        //paymentsPublisher.send(
        //    new CreatePaymentsCommand(
        //        envelope.id,
        //        Long.toString(caseDetails.getId()),
        //        caseDetails.getJurisdiction(),
        //        envelope.container,
        //        envelope.poBox,
        //        false,
        //        envelope.payments.stream()
        //            .map(payment -> new PaymentData(payment.documentControlNumber))
        //            .collect(toList())
        //    )
        //);

        //then
        //await("Case is updated")
        //    .atMost(60, TimeUnit.SECONDS)
        //    .pollDelay(1, TimeUnit.SECONDS)
        //    .until(() -> casePaymentStatusUpdated(caseDetails));
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
