package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;

class CallbackValidationsTest {

    private static Object[][] caseReferenceTestParams() {
        return new Object[][]{
            {caseWithReference("£1234234393"), true, "1234234393"},
            {caseWithReference("1234-234-393"), true, "1234234393"},
            {caseWithReference("1234 234 393"), true, "1234234393"},
            {caseWithReference("  AH1234 234 393"), true, "1234234393"},
            {caseWithReference("#"), false, "Invalid case reference: '#'"},
            {caseWithReference(""), false, "Invalid case reference: ''"},
            {null, false, "No case reference supplied"},
            {createCaseWith(b -> b.data(null)), false, "No case reference supplied"},
            {caseWithReference(null), false, "No case reference supplied"},
        };
    }

    @ParameterizedTest(name = "valid:{1} value:{2}")
    @MethodSource("caseReferenceTestParams")
    @DisplayName("Should accept and remove non 0-9 chars in the case reference")
    void caseReferenceTest(CaseDetails input, boolean valid, String realValue) {
        Validation<String, String> validation = CallbackValidations.hasCaseReference(input);
        if (valid) {
            assertSoftly(softly -> {
                softly.assertThat(validation.isValid()).isTrue();
                softly.assertThat(validation.get()).isEqualTo(realValue);
            });
        } else {
            assertSoftly(softly -> {
                softly.assertThat(validation.isValid()).isFalse();
                softly.assertThat(validation.getError()).isEqualTo(realValue);
            });
        }
    }


}
