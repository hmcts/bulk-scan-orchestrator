package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class PaymentToExistingCaseTest {

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private PaymentsPublisher paymentsPublisher;

    private String dmUrl;

    private String documentUuid;

    @Test
    public void should_attach_exception_record_to_the_existing_case_with_no_evidence() throws Exception {
        //given
        CaseDetails caseDetails =
            ccdCaseCreator.createCase(
                emptyList(),
                Instant.now(),
                ImmutableMap.of("awaitingPaymentDCNProcessing", "Yes")
            );
        assertThat(caseDetails.getData().get("awaitingPaymentDCNProcessing")).isEqualTo("Yes");

        // when
        // message sent to payments queue
        paymentsPublisher.send(
            new CreatePaymentsCommand(
                envelope.id,
                Long.toString(caseDetails.getId()),
                caseDetails.getJurisdiction(),
                envelope.container,
                envelope.poBox,
                false,
                envelope.payments.stream()
                    .map(payment -> new PaymentData(payment.documentControlNumber))
                    .collect(toList())
            )
        );

        //then
        await("Case is ingested")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .until(() -> casePaymentStatusUIpdated(caseDetails));
    }

    private Boolean casePaymentStatusUIpdated(CaseDetails caseDetails) {
        CaseDetails caseDetailsUpdated =
            ccdApi.getCase(Long.toString(caseDetails.getId()), caseDetails.getJurisdiction());
        return caseDetailsUpdated.getData().get("awaitingPaymentDCNProcessing").equals("No");
    }
}
