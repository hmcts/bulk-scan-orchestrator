package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResultType.ABANDONED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResultType.ERROR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResultType.OK;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@SuppressWarnings("checkstyle:LineLength")
@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceWithOcrHandlerTest {

    @Mock CreateExceptionRecord exceptionRecordCreator;
    @Mock PaymentsService paymentsService;
    @Mock AutoCaseUpdater autoCaseUpdater;
    @Mock ServiceConfigProvider serviceConfigProvider;

    SupplementaryEvidenceWithOcrHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SupplementaryEvidenceWithOcrHandler(
            exceptionRecordCreator,
            paymentsService,
            autoCaseUpdater,
            serviceConfigProvider
        );
    }

    @Test
    void should_create_exception_record_if_auto_case_update_is_disabled() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        givenAutoCaseUpdateEnabled(envelope, false);
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope, 0);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsService).createNewPayment(envelope, CASE_ID, true);
    }

    @Test
    void should_update_case_if_auto_case_update_is_enabled() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        Long existingCaseId = 111000L;
        givenAutoCaseUpdateEnabled(envelope, true);
        given(autoCaseUpdater.updateCase(envelope)).willReturn(new AutoCaseUpdateResult(OK, existingCaseId));

        // when
        EnvelopeProcessingResult result = handler.handle(envelope, 0);

        // then
        assertThat(result.ccdId).isEqualTo(existingCaseId);
        assertThat(result.envelopeCcdAction).isEqualTo(EnvelopeCcdAction.AUTO_UPDATED_CASE);

        verify(paymentsService).createNewPayment(envelope, existingCaseId, false);
    }

    @Test
    void should_create_exception_record_if_auto_case_update_is_enabled_but_case_cannot_be_updated() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        givenAutoCaseUpdateEnabled(envelope, true);
        given(autoCaseUpdater.updateCase(envelope)).willReturn(new AutoCaseUpdateResult(ABANDONED, null));
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope, 0);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsService).createNewPayment(envelope, CASE_ID, true);
    }

    @Test
    void should_throw_an_exception_if_an_error_occurred_while_updating_a_case_and_max_number_of_retries_was_not_reached() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        givenAutoCaseUpdateEnabled(envelope, true);
        given(autoCaseUpdater.updateCase(envelope)).willReturn(new AutoCaseUpdateResult(ERROR, null));

        // when
        var exc = catchThrowable(() -> handler.handle(envelope, 0));

        // then
        assertThat(exc)
                .isInstanceOf(CaseUpdateException.class)
                .hasMessageContaining("Updating case failed due to a potentially recoverable error");
    }

    @Test
    void should_create_exception_record_if_an_error_occurred_while_updating_a_case_and_max_number_of_retries_was_reached() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        givenAutoCaseUpdateEnabled(envelope, true);
        given(autoCaseUpdater.updateCase(envelope)).willReturn(new AutoCaseUpdateResult(ERROR, null));
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope, SupplementaryEvidenceWithOcrHandler.MAX_RETRIES);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsService).createNewPayment(envelope, CASE_ID, true);
    }

    @Test
    void should_throw_an_exception_if_envelope_classification_is_incorrect() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);

        // when
        var exc = catchThrowable(() -> handler.handle(envelope, 0));

        // then
        assertThat(exc)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Envelope classification")
            .hasMessageContaining(SUPPLEMENTARY_EVIDENCE_WITH_OCR.toString());
    }

    void givenAutoCaseUpdateEnabled(Envelope envelope, boolean enabled) {
        var configItem = mock(ServiceConfigItem.class);
        given(serviceConfigProvider.getConfig(envelope.container)).willReturn(configItem);
        given(configItem.getAutoCaseUpdateEnabled()).willReturn(enabled);
    }
}
