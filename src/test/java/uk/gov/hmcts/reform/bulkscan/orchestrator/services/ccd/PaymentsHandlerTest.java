package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;

@ExtendWith(MockitoExtension.class)
class PaymentsHandlerTest {
    @Mock
    private IPaymentsPublisher paymentsPublisher;

    private PaymentsHandler paymentsHandler;

    private static final String SERVICE = "service";
    private static final long CASE_ID = 123;
    private static final long NEW_CASE_ID = 1L;
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    @BeforeEach
    void setUp() {
        paymentsHandler = new PaymentsHandler(paymentsPublisher);
    }

    @Test
    void should_send_payment_message_when_case_has_payments() {
        // given
        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "A1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.YES);
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);
        data.put(ExceptionRecordFields.PO_BOX_JURISDICTION, jurisdiction);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(CASE_ID)
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );

        // when
        paymentsHandler.handlePayments(caseDetails, NEW_CASE_ID);

        // then
        ArgumentCaptor<UpdatePaymentsCommand> cmd = ArgumentCaptor.forClass(UpdatePaymentsCommand.class);
        verify(paymentsPublisher).send(cmd.capture());
        assertThat(cmd.getValue().exceptionRecordRef).isEqualTo(Long.toString(CASE_ID));
        assertThat(cmd.getValue().newCaseRef).isEqualTo(Long.toString(NEW_CASE_ID));
        assertThat(cmd.getValue().envelopeId).isEqualTo(envelopeId);
        assertThat(cmd.getValue().jurisdiction).isEqualTo(jurisdiction);
    }

    @Test
    void should_not_send_payment_message_when_case_has_no_payments() {
        // given
        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "A1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.NO); // no payments!
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);
        data.put(ExceptionRecordFields.PO_BOX_JURISDICTION, jurisdiction);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(CASE_ID)
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );


        // when
        paymentsHandler.handlePayments(caseDetails,1L);

        // then
        verify(paymentsPublisher, never()).send(any());
    }
}
