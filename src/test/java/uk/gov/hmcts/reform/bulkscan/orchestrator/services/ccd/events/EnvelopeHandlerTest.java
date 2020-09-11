package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

@ExtendWith(MockitoExtension.class)
class EnvelopeHandlerTest {

    @Mock ExceptionClassificationHandler exceptionClassificationHandler;
    @Mock SupplementaryEvidenceWithOcrHandler supplementaryEvidenceWithOcrHandler;
    @Mock NewApplicationHandler newApplicationHandler;
    @Mock SupplementaryEvidenceHandler supplementaryEvidenceHandler;

    EnvelopeHandler envelopeHandler;

    @BeforeEach
    void setUp() {
        envelopeHandler = new EnvelopeHandler(
            exceptionClassificationHandler,
            supplementaryEvidenceWithOcrHandler,
            newApplicationHandler,
            supplementaryEvidenceHandler
        );
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            exceptionClassificationHandler,
            supplementaryEvidenceWithOcrHandler,
            newApplicationHandler,
            supplementaryEvidenceHandler
        );
    }

    @ParameterizedTest()
    @EnumSource(Classification.class)
    void should_pass_envelope_to_appropriate_handler_based_on_classification(Classification classification) {
        // given
        Envelope envelope = envelope(classification, JURSIDICTION, CASE_REF);
        int deliveryCount = 5;

        //when
        envelopeHandler.handleEnvelope(envelope, deliveryCount);

        // then
        switch (classification) {
            case EXCEPTION:
                verify(exceptionClassificationHandler).handle(envelope);
                break;
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
