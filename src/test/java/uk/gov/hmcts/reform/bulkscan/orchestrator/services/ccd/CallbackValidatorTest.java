package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;

@SuppressWarnings("checkstyle:LineLength")
@ExtendWith(MockitoExtension.class)
class CallbackValidatorTest {
    private static final String SERVICE = "service";
    private static final String CASE_ID = "123";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";
    private static final String NO_CASE_TYPE_ID_SUPPLIED_ERROR = "No case type ID supplied";

    @Mock
    private CaseReferenceValidator caseReferenceValidator;

    private CallbackValidator callbackValidator;

    @BeforeEach
    void setUp() {
        callbackValidator = new CallbackValidator(caseReferenceValidator);
    }

    @Test
    void hasCaseTypeId_returns_valid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .caseTypeId(CASE_TYPE_ID)
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasCaseTypeId(caseDetails);

        // then
        assertThat(res.isValid()).isTrue();
        assertThat(res.get()).isEqualTo(CASE_TYPE_ID);
    }

    @Test
    void hasCaseTypeId_returns_invalid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasCaseTypeId(caseDetails);

        // then
        assertThat(res.isValid()).isFalse();
        assertThat(res.getError()).isEqualTo("Missing caseType");
    }

    @Test
    void hasFormType_returns_valid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .data(Map.of("formType", "B123"))
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasFormType(caseDetails);

        // then
        assertThat(res.isValid()).isTrue();
        assertThat(res.get()).isEqualTo("B123");
    }

    @Test
    void hasFormType_returns_invalid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .data(emptyMap())
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasFormType(caseDetails);

        // then
        assertThat(res.isValid()).isFalse();
        assertThat(res.getError()).isEqualTo("Missing Form Type");
    }

    @Test
    void hasJurisdiction_returns_valid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasJurisdiction(caseDetails);

        // then
        assertThat(res.isValid()).isTrue();
        assertThat(res.get()).isEqualTo("some jurisdiction");
    }

    @Test
    void hasJurisdiction_returns_invalid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
        );

        // when
        Validation<String, String> res = callbackValidator.hasJurisdiction(caseDetails);

        // then
        assertThat(res.isValid()).isFalse();
        assertThat(res.getError()).isEqualTo("Internal Error: invalid jurisdiction supplied: null");
    }

    @Test
    void hasTargetCaseReference_calls_caseReferenceValidator() {
        // given
        CaseDetails caseDetails = mock(CaseDetails.class);
        Validation<String, String> validationRes = Validation.valid("caseRef");
        given(caseReferenceValidator.validateTargetCaseReference(any(CaseDetails.class))).willReturn(validationRes);

        // when
        Validation<String,String> res = callbackValidator.hasTargetCaseReference(caseDetails);

        // then
        assertThat(res).isSameAs(validationRes);
    }

    @Test
    void hasSearchCaseReference_calls_caseReferenceValidator() {
        // given
        CaseDetails caseDetails = mock(CaseDetails.class);
        Validation<String, String> validationRes = Validation.valid("caseRef");
        given(caseReferenceValidator.validateSearchCaseReferenceWithSearchType(any(CaseDetails.class)))
                .willReturn(validationRes);

        // when
        Validation<String,String> res = callbackValidator.hasSearchCaseReference(caseDetails);

        // then
        assertThat(res).isSameAs(validationRes);
    }

    @Test
    void hasSearchCaseReferenceType_calls_caseReferenceValidator() {
        // given
        CaseDetails caseDetails = mock(CaseDetails.class);
        Validation<String, String> validationRes = Validation.valid("caseRef");
        given(caseReferenceValidator.validateCaseReferenceType(any(CaseDetails.class))).willReturn(validationRes);

        // when
        Validation<String,String> res = callbackValidator.hasSearchCaseReferenceType(caseDetails);

        // then
        assertThat(res).isSameAs(validationRes);
    }

    @Test
    void noCaseIdTest() {
        checkValidation(
                createCaseWith(b -> b.id(null)),
                false,
                null,
                callbackValidator::hasAnId,
                "Exception case has no Id"
        );
    }

    @Test
    void valid_case_id_should_pass() {
        checkValidation(
                createCaseWith(b -> b.id(1L)),
                true,
                1L,
                callbackValidator::hasAnId,
                null
        );
    }

    @Test
    void no_case_details_should_fail() {
        checkValidation(
                null,
                false,
                1L,
                callbackValidator::hasAnId,
                "Exception case has no Id"
        );
    }

    private static Object[][] caseTypeIdTestParams() {
        return new Object[][] {
                {"null case details", null, false, NO_CASE_TYPE_ID_SUPPLIED_ERROR},
                {"null case type ID", createCaseWith(b -> b.data(null)), false, NO_CASE_TYPE_ID_SUPPLIED_ERROR},
                {"case type ID with wrong suffix", createCaseWith(b -> b.caseTypeId("service_exceptionrecord")), false, "Case type ID (service_exceptionrecord) has invalid format"},
                {"case type ID being just sufifx", createCaseWith(b -> b.caseTypeId("_ExceptionRecord")), false, "Case type ID (_ExceptionRecord) has invalid format"},
                {"valid case type ID", createCaseWith(b -> b.caseTypeId("SERVICE_ExceptionRecord")), true, "service"},
                {"case type ID with underscores", createCaseWith(b -> b.caseTypeId("LONG_SERVICE_NAME_ExceptionRecord")), true, "long_service_name"},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("caseTypeIdTestParams")
    @DisplayName("Should accept valid case type ID")
    void serviceNameInCaseTypeIdTest(
            String caseDescription,
            CaseDetails inputCase,
            boolean valid,
            String expectedValueOrError
    ) {
        checkValidation(
                inputCase,
                valid,
                expectedValueOrError,
                callbackValidator::hasServiceNameInCaseTypeId,
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
