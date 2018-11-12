package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.document;

class CallbackValidationsTest {

    private static Object[][] caseReferenceTestParams() {
        String noReferenceSupplied = "No case reference supplied";
        return new Object[][]{
            {"generic non number removal", caseWithReference("£1234234393"), true, "1234234393"},
            {"- removal", caseWithReference("1234-234-393"), true, "1234234393"},
            {"space removal", caseWithReference("1234 234 393"), true, "1234234393"},
            {"prefix and post fix spaces removal", caseWithReference("  AH 234 393 "), true, "234393"},
            {"No numbers supplied", caseWithReference("#"), false, "Invalid case reference: '#'"},
            {"empty string", caseWithReference(""), false, "Invalid case reference: ''"},
            {"null case details", null, false, noReferenceSupplied},
            {"null data", createCaseWith(b -> b.data(null)), false, noReferenceSupplied},
            {"empty data", createCaseWith(b -> b.data(ImmutableMap.of())), false, noReferenceSupplied},
            {"null case reference", caseWithReference(null), false, noReferenceSupplied},
            {"invalid type List", caseWithReference(ImmutableList.of()), false, "Invalid case reference: '[]'"},
            {"invalid type Integer", caseWithReference(5), false, "Invalid case reference: '5'"},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("caseReferenceTestParams")
    @DisplayName("Should accept and remove non 0-9 chars in the case reference")
    void caseReferenceTest(String caseReason, CaseDetails input, boolean valid, String realValue) {
        checkValidation(input, valid, realValue, CallbackValidations::hasCaseReference, realValue);
    }


    private static Object[][] scannedRecordTestParams() {
        String noDocumentError = "There were no documents in exception record";
        CaseDetails validDoc = caseWithDocument(document("fileName.pdf"));
        return new Object[][]{
            {"Correct map with document", validDoc, true, document("fileName.pdf"), null},
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
    void scannedRecordTest(String caseReason,
                           CaseDetails input,
                           boolean valid,
                           List<Map<String, Object>> realValue,
                           String errorString) {
        checkValidation(input, valid, realValue, CallbackValidations::hasAScannedRecord, errorString);
    }

    private <T> void checkValidation(CaseDetails input,
                                     boolean valid,
                                     T realValue,
                                     Function<CaseDetails, Validation<String, T>> validationMethod,
                                     String errorString) {
        Validation<String, T> validation = validationMethod.apply(input);
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
