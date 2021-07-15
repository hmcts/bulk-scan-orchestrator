package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithDocument;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.document;

class ScannedDocumentValidatorTest {

    private ScannedDocumentValidator scannedDocumentValidator;

    @BeforeEach
    void setUp() {
        scannedDocumentValidator = new ScannedDocumentValidator();
    }

    private static Object[][] scannedRecordTestParams() {
        String noDocumentError = "There were no documents in exception record";
        CaseDetails validDoc = caseWithDocument(document("https://url", "fileName.pdf"));
        return new Object[][]{
                {"Correct map with document", validDoc, true, document("https://url", "fileName.pdf"), null},
                {"Null case details", null, false, null, noDocumentError},
                {"Null data supplied", createCaseWith(b -> b.data(null)), false, null, noDocumentError},
                {"Empty data supplied", createCaseWith(b -> b.data(ImmutableMap.of())), false, null, noDocumentError},
                {"Null case document list", caseWithDocument(null), false, null, noDocumentError},
                {"No items in document list", caseWithDocument(emptyList()), false, null, noDocumentError},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error:{4}")
    @MethodSource("scannedRecordTestParams")
    @DisplayName("Should check that at least one scanned record exists")
    void scannedRecordTest(
            String caseReason,
            CaseDetails input,
            boolean valid,
            List<Map<String, Object>> realValue,
            String errorString
    ) {
        checkValidation(input, valid, realValue, scannedDocumentValidator::validate, errorString);
    }

    private <T> void checkValidation(
            CaseDetails input,
            boolean valid,
            T realValue,
            Function<CaseDetails, Validation<String, ?>> validationMethod,
            String errorString
    ) {
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
