package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@SuppressWarnings("checkstyle:LineLength")
@ExtendWith(MockitoExtension.class)
class EnvelopeHandlerTest {

    @Mock private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;
    @Mock private CreateExceptionRecord createExceptionRecord;
    @Mock private CaseFinder caseFinder;
    @Mock private PaymentsProcessor paymentsProcessor;
    @Mock private NewApplicationHandler newApplicationHandler;
    @Mock private SupplementaryEvidenceHandler supplementaryEvidenceHandler;
    @Mock private SupplementaryEvidenceWithOcrHandler supplementaryEvidenceWithOcrHandler;

    private EnvelopeHandler envelopeHandler;

    @BeforeEach
    void setUp() {
        envelopeHandler = new EnvelopeHandler(
            createExceptionRecord,
            paymentsProcessor,
            supplementaryEvidenceWithOcrHandler,
            newApplicationHandler,
            supplementaryEvidenceHandler
        );
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord,
            caseFinder,
            paymentsProcessor
        );
    }

    @Test
    void should_call_CreateExceptionRecord_for_exception_classification() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @ParameterizedTest()
    @EnumSource(value = Classification.class, names = {"NEW_APPLICATION", "SUPPLEMENTARY_EVIDENCE", "SUPPLEMENTARY_EVIDENCE_WITH_OCR"})
    void should_pass_envelope_to_appropriate_handler_based_on_classification(Classification classification) {
        // given
        Envelope envelope = envelope(classification, JURSIDICTION, CASE_REF);
        int deliveryCount = 5;

        //when
        envelopeHandler.handleEnvelope(envelope, deliveryCount);

        // then
        switch (classification) {
            case NEW_APPLICATION:
                verify(newApplicationHandler).handle(envelope, deliveryCount);
                break;
            case SUPPLEMENTARY_EVIDENCE:
                verify(supplementaryEvidenceHandler).handle(envelope);
                break;
            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
                verify(supplementaryEvidenceWithOcrHandler).handle(envelope);
                break;
            default:
                break;
        }
    }
}
