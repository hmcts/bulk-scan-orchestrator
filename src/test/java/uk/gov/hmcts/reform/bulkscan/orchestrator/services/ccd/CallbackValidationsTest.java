package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@SuppressWarnings("checkstyle:LineLength")
class CallbackValidationsTest {

    public static final String JOURNEY_CLASSIFICATION = "journeyClassification";
    public static final String CLASSIFICATION_EXCEPTION = "EXCEPTION";


    private static Object[][] classificationForAttachToCaseTestParams() {
        return new Object[][]{
            {"Invalid journey classification", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, "invalid_classification"))), false, null, "Journey Classification invalid_classification is not allowed when attaching exception record to a case"},
            {"Invalid journey classification", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, NEW_APPLICATION.name()))), false, null, "The current Journey Classification NEW_APPLICATION is not allowed for attaching to case"},
            {"Valid journey classification(supplementary evidence)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE.name()))), true, SUPPLEMENTARY_EVIDENCE, null},
            {"Valid journey classification(supplementary evidence with ocr)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name(), OCR_DATA, asList(ImmutableMap.of("f1", "v1"))))), true, SUPPLEMENTARY_EVIDENCE_WITH_OCR, null},
            {"Valid journey classification(exception)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, CLASSIFICATION_EXCEPTION))), true, EXCEPTION, null}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} value:{3} error:{4}")
    @MethodSource("classificationForAttachToCaseTestParams")
    @DisplayName("Should accept valid journey classification")
    void canJourneyClassificationBeAttachedToCaseTest(
        String caseDescription,
        CaseDetails inputCase,
        boolean valid,
        Classification expectedValue,
        String error
    ) {
        checkValidation(
            inputCase,
            valid,
            expectedValue,
            CallbackValidations::hasJourneyClassificationForAttachToCase,
            error
        );
    }

    private <T> void checkValidation(CaseDetails input,
                                     boolean valid,
                                     T realValue,
                                     Function<CaseDetails, Validation<String, ?>> validationMethod,
                                     String errorString) {
        Validation<String, ?> validation = validationMethod.apply(input);

        softAssertions(valid, realValue, errorString, validation);
    }

    private <T> void softAssertions(boolean valid, T realValue, String errorString, Validation<String, ?> validation) {
        if (valid) {
            assertSoftly(softly -> {
                softly.assertThat(validation.isValid()).isTrue();
                softly.assertThat(validation.get()).isEqualTo(realValue);
            });
        } else {
            assertSoftly(softly -> {
                softly.assertThat(validation.isValid()).isFalse();
                softly.assertThat(validation.getError()).isEqualTo(errorString);
            });
        }
    }
}
