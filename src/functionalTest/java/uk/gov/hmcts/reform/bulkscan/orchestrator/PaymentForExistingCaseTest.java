package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;

import static java.util.Collections.emptyList;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class PaymentToExistingCaseTest {

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    //@Autowired
    //private PaymentsPublisher paymentsPublisher;

    @Test
    public void should_set_awaiting_payment_false_after_payment_sent() throws Exception {
        //given
        CaseDetails caseDetails =
            ccdCaseCreator.createCase(
                emptyList(),
                Instant.now(),
                ImmutableMap.of("awaitingPaymentDCNProcessing", "Yes")
            );
        //assertThat(caseDetails.getData().get("awaitingPaymentDCNProcessing")).isEqualTo("Yes");
        //CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList(), Instant.now());

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
}
