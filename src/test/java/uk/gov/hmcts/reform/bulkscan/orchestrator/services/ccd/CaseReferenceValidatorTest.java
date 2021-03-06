package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.function.Function;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithCcdSearchCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithExternalSearchCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithSearchCaseRefTypeAndCaseRef;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithSearchCaseReferenceType;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithTargetReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;

@SuppressWarnings("checkstyle:LineLength")
class CaseReferenceValidatorTest {

    public static final String NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR = "No case reference type supplied";

    private CaseReferenceValidator caseReferenceValidator;

    @BeforeEach
    void setUp() {
        caseReferenceValidator = new CaseReferenceValidator();
    }

    private static Object[][] attachToCaseReferenceTestParams() {
        String noReferenceSupplied = "No case reference supplied";
        return new Object[][]{
                {"generic non number removal", caseWithTargetReference("£1234234393"), true, "1234234393"},
                {"- removal", caseWithTargetReference("1234-234-393"), true, "1234234393"},
                {"space removal", caseWithTargetReference("1234 234 393"), true, "1234234393"},
                {"prefix and post fix spaces removal", caseWithTargetReference("  AH 234 393 "), true, "234393"},
                {"No numbers supplied", caseWithTargetReference("#"), false, "Invalid case reference: '#'"},
                {"empty string", caseWithTargetReference(""), false, "Invalid case reference: ''"},
                {"null case details", null, false, noReferenceSupplied},
                {"null data", createCaseWith(b -> b.data(null)), false, noReferenceSupplied},
                {"empty data", createCaseWith(b -> b.data(ImmutableMap.of())), false, noReferenceSupplied},
                {"null case reference", caseWithTargetReference(null), false, noReferenceSupplied},
                {"invalid type List", caseWithTargetReference(ImmutableList.of()), false, "Invalid case reference: '[]'"},
                {"invalid type Integer", caseWithTargetReference(5), false, "Invalid case reference: '5'"},
                {"valid search case reference", caseWithTargetReference("1234 234 393"), true, "1234234393"},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("attachToCaseReferenceTestParams")
    @DisplayName("Should accept and remove non 0-9 chars in the case reference")
    void attachToCaseReferenceTest(String caseReason, CaseDetails input, boolean valid, String realValue) {
        checkValidation(input, valid, realValue, caseReferenceValidator::validateTargetCaseReference, realValue);
    }

    private static Object[][] searchCaseReferenceTestParams() {
        String noReferenceSupplied = "No case reference supplied";
        String noValidReferenceType = "Cannot validate case reference due to the lack of valid case reference type";
        return new Object[][]{
                {"null case details", null, false, noValidReferenceType},
                {"null data", createCaseWith(b -> b.data(null)), false, noValidReferenceType},
                {"empty data", createCaseWith(b -> b.data(ImmutableMap.of())), false, noValidReferenceType},
                {"no search case reference type", createCaseWith(b -> b.data(ImmutableMap.of("searchCaseReference", "12345"))), false, noValidReferenceType},
                {"invalid search case reference type", caseWithSearchCaseRefTypeAndCaseRef("invalid-type", "12345"), false, noValidReferenceType},

                // test cases for case reference type == ccdCaseReference
                {"CCD ref: generic non number removal", caseWithCcdSearchCaseReference("£1234234393"), true, "1234234393"},
                {"CCD ref: - removal", caseWithCcdSearchCaseReference("1234-234-393"), true, "1234234393"},
                {"CCD ref: space removal", caseWithCcdSearchCaseReference("1234 234 393"), true, "1234234393"},
                {"CCD ref: prefix and post fix spaces removal", caseWithCcdSearchCaseReference("  AH 234 393 "), true, "234393"},
                {"CCD ref: No numbers supplied", caseWithCcdSearchCaseReference("#"), false, "Invalid case reference: '#'"},
                {"CCD ref: empty string", caseWithCcdSearchCaseReference(""), false, "Invalid case reference: ''"},
                {"CCD ref: null case reference", caseWithCcdSearchCaseReference(null), false, noReferenceSupplied},
                {"CCD ref: invalid type List", caseWithCcdSearchCaseReference(ImmutableList.of()), false, "Invalid case reference: '[]'"},
                {"CCD ref: invalid type Integer", caseWithCcdSearchCaseReference(5), false, "Invalid case reference: '5'"},

                // test cases for case reference type == externalCaseReference
                {"External ref: non-empty string", caseWithExternalSearchCaseReference("23424"), true, "23424"},
                {"External ref: empty string", caseWithExternalSearchCaseReference(""), false, "Invalid external case reference: ''"},
                {"External ref: prefix and post fix spaces removal", caseWithExternalSearchCaseReference(" AH 234 393 "), true, "AH 234 393"},
                {"External ref: null case reference", caseWithExternalSearchCaseReference(null), false, noReferenceSupplied},
                {"External ref: invalid type List", caseWithExternalSearchCaseReference(ImmutableList.of()), false, "Invalid external case reference: '[]'"},
                {"External ref: invalid type Integer", caseWithExternalSearchCaseReference(5), false, "Invalid external case reference: '5'"},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("searchCaseReferenceTestParams")
    @DisplayName("Should accept valid case reference")
    void searchCaseReferenceTest(
            String caseDescription,
            CaseDetails inputCase,
            boolean valid,
            String expectedValueOrError
    ) {
        checkValidation(
                inputCase,
                valid,
                expectedValueOrError,
                caseReferenceValidator::validateSearchCaseReferenceWithSearchType,
                expectedValueOrError
        );
    }

    private static Object[][] searchCaseReferenceTypeTestParams() {
        return new Object[][]{
                {"null case details", null, false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
                {"null data", createCaseWith(b -> b.data(null)), false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
                {"data not containing reference type", createCaseWith(b -> b.data(ImmutableMap.of())), false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
                {"invalid search case reference type", caseWithSearchCaseReferenceType("invalid-type"), false, "Invalid case reference type supplied: invalid-type"},
                {"null search case reference type", caseWithSearchCaseReferenceType(null), false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
                {"non-string search case reference type", caseWithSearchCaseReferenceType(321), false, "Invalid case reference type supplied: 321"},
                {"CCD case reference type", caseWithSearchCaseReferenceType("ccdCaseReference"), true, "ccdCaseReference"},
                {"External case reference type", caseWithSearchCaseReferenceType("externalCaseReference"), true, "externalCaseReference"}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("searchCaseReferenceTypeTestParams")
    @DisplayName("Should accept valid case reference type")
    void searchCaseReferenceTypeTest(
            String caseDescription,
            CaseDetails inputCase,
            boolean valid,
            String expectedValueOrError
    ) {
        checkValidation(
                inputCase,
                valid,
                expectedValueOrError,
                caseReferenceValidator::validateCaseReferenceType,
                expectedValueOrError
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
