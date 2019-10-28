package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ExceptionRecordCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class PaymentForExistingCaseTest {

    public static final String AWAITING_PAYMENT_DCN_PROCESSING = "awaitingPaymentDCNProcessing";
    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private ExceptionRecordCreator exceptionRecordCreator;

    @Autowired
    private IPaymentsPublisher paymentsPublisher;

    @Autowired
    private CaseSearcher caseSearcher;

    @Test
    public void should_set_awaiting_payment_false_after_payment_sent() throws Exception {
        // given
        CaseDetails caseDetails = exceptionRecordCreator.createExceptionRecord(
            ImmutableMap.of(AWAITING_PAYMENT_DCN_PROCESSING, YES)
        );

        assertThat(caseDetails.getData().get(AWAITING_PAYMENT_DCN_PROCESSING)).isEqualTo(YES);

        // when
        // payment sent to payments queue
        paymentsPublisher.send(
            new CreatePaymentsCommand(
                "envelope_id",
                Long.toString(caseDetails.getId()),
                caseDetails.getJurisdiction(),
                "bulkscan",
                "po_box",
                true,
                asList(new PaymentData("154565768"))
            )
        );

        //then
        await("Case is updated")
            .atMost(30, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .until(() -> casePaymentStatusUpdated(caseDetails));
    }

    private Boolean casePaymentStatusUpdated(CaseDetails caseDetails) {
        CaseDetails caseDetailsUpdated =
            ccdApi.getCase(Long.toString(caseDetails.getId()), caseDetails.getJurisdiction());
        return caseDetailsUpdated.getData().get(AWAITING_PAYMENT_DCN_PROCESSING).equals(NO);
    }
}
