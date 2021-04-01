package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.AutoCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResult.caseCreated;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResult.potentiallyRecoverableFailure;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers.NewApplicationHandler.MAX_RETRIES_FOR_POTENTIALLY_RECOVERABLE_FAILURES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class NewApplicationHandlerTest {

    @Mock AutoCaseCreator autoCaseCreator;
    @Mock PaymentsProcessor paymentsProcessor;
    @Mock CreateExceptionRecord exceptionRecordCreator;

    NewApplicationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NewApplicationHandler(autoCaseCreator, paymentsProcessor, exceptionRecordCreator);
    }

    @Test
    void should_not_create_exception_record_when_case_creation_was_successful() {
        // given
        long caseId = 1234L;
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(caseCreated(caseId));

        // when
        EnvelopeProcessingResult result = handler.handle(envelope, 0);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EnvelopeCcdAction.AUTO_CREATED_CASE);
        assertThat(result.ccdId).isEqualTo(caseId);

        verify(autoCaseCreator).createCase(envelope);
        verify(exceptionRecordCreator, never()).tryCreateFrom(any());
        verify(paymentsProcessor).createPayments(envelope, caseId, false);
    }

    @Test
    void should_create_exception_record_when_unrecoverable_failure() {
        // given
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(CaseCreationResult.unrecoverableFailure());
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope, 0);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(autoCaseCreator).createCase(envelope);
        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_throw_exception_record_for_new_application_when_recoverable_failure() {
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(potentiallyRecoverableFailure());

        assertThatThrownBy(() -> handler.handle(envelope, 0))
            .isInstanceOf(CaseCreationException.class)
            .hasMessage("Case creation failed due to a potentially recoverable error");

        verify(autoCaseCreator).createCase(envelope);
    }

    @Test
    void should_create_exception_record_for_new_application_when_too_many_deliveries() {
        // given
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(potentiallyRecoverableFailure());
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope, MAX_RETRIES_FOR_POTENTIALLY_RECOVERABLE_FAILURES);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(autoCaseCreator).createCase(envelope);
        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_throw_an_exception_if_envelope_is_not_a_new_application() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);

        // when
        var exc = catchThrowable(() -> handler.handle(envelope, 0));

        // then
        assertThat(exc)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Envelope classification")
            .hasMessageContaining(NEW_APPLICATION.toString());
    }
}
