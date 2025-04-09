package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.launchdarkly.LaunchDarklyClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentsProcessorTest {
    private static final long CCD_REFERENCE = 20L;
    private static final String SERVICE = "service";
    private static final String CASE_ID = "123";
    private static final String NEW_CASE_ID = "1";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    @Mock
    private PaymentsPublisher paymentsPublisher;

    @Mock
    private LaunchDarklyClient launchDarklyClient;

    @Mock
    private PaymentsService paymentsService;

    private PaymentsProcessor paymentsProcessor;

    @BeforeEach
    void setUp() {
        paymentsProcessor = new PaymentsProcessor(paymentsPublisher, launchDarklyClient, paymentsService);
    }

    @Test
    void calls_payments_publisher_if_envelope_contains_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            asList(new Payment("dcn1")),
            emptyList(),
            emptyList()
        );
        ArgumentCaptor<CreatePaymentsCommand> cmdCaptor = ArgumentCaptor.forClass(CreatePaymentsCommand.class);

        // when
        paymentsProcessor.createPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentsPublisher).send(cmdCaptor.capture());
        CreatePaymentsCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.ccdReference).isEqualTo(Long.toString(CCD_REFERENCE));
        assertThat(cmd.jurisdiction).isEqualTo(envelope.jurisdiction);
        assertThat(cmd.poBox).isEqualTo(envelope.poBox);
        assertThat(cmd.isExceptionRecord).isTrue();
        assertThat(cmd.payments.size()).isEqualTo(envelope.payments.size());
        assertThat(cmd.payments.get(0).documentControlNumber)
            .isEqualTo(envelope.payments.get(0).documentControlNumber);
    }

    @Test
    void does_not_call_payments_publisher_if_envelope_contains_zero_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            emptyList(),
            emptyList(),
            emptyList()
        );

        // when
        paymentsProcessor.createPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentsPublisher, never()).send(any(CreatePaymentsCommand.class));
    }

    @Test
    void does_not_call_payments_publisher_if_envelope_contains_null_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            null,
            emptyList(),
            emptyList()
        );

        // when
        paymentsProcessor.createPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentsPublisher, never()).send(any(CreatePaymentsCommand.class));
    }

    @Test
    void should_send_payment_message_when_case_has_payments() {
        // given
        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.YES);
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(Long.valueOf(CASE_ID))
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );

        // when
        paymentsProcessor.updatePayments(PaymentsHelper.create(caseDetails), CASE_ID, jurisdiction, NEW_CASE_ID);

        // then
        ArgumentCaptor<UpdatePaymentsCommand> cmd = ArgumentCaptor.forClass(UpdatePaymentsCommand.class);
        verify(paymentsPublisher).send(cmd.capture());
        assertThat(cmd.getValue().exceptionRecordRef).isEqualTo(CASE_ID);
        assertThat(cmd.getValue().newCaseRef).isEqualTo(NEW_CASE_ID);
        assertThat(cmd.getValue().envelopeId).isEqualTo(envelopeId);
        assertThat(cmd.getValue().jurisdiction).isEqualTo(jurisdiction);
    }

    @Test
    void should_not_send_payment_message_when_case_has_no_payments() {
        // given
        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.NO); // no payments!
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(Long.valueOf(CASE_ID))
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );


        // when
        paymentsProcessor.updatePayments(PaymentsHelper.create(caseDetails), CASE_ID, jurisdiction, NEW_CASE_ID);

        // then
        verify(paymentsPublisher, never()).send(any());
    }
}
