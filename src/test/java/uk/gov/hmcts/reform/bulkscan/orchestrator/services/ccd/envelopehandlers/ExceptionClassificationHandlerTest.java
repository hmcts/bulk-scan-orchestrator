package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class ExceptionClassificationHandlerTest {

    @Mock CreateExceptionRecord exceptionRecordCreator;
    @Mock PaymentsService paymentsService;

    ExceptionClassificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExceptionClassificationHandler(exceptionRecordCreator, paymentsService);
    }

    @Test
    void should_create_exception_record() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsService).createNewPayment(envelope, CASE_ID, true);
    }

    @Test
    void should_throw_exception_if_envelope_is_not_of_exception_classification() {
        // given
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);

        // when
        var exc = catchThrowable(() -> handler.handle(envelope));

        // then
        assertThat(exc)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Envelope classification");
    }
}
