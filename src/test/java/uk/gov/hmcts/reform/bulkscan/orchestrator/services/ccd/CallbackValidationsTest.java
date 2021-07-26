package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.function.Function;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SuppressWarnings("checkstyle:LineLength")
class CallbackValidationsTest {

    public static final String JOURNEY_CLASSIFICATION = "journeyClassification";
    public static final String CLASSIFICATION_EXCEPTION = "EXCEPTION";

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
