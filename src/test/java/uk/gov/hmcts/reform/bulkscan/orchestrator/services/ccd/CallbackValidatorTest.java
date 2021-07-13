package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CallbackValidatorTest {
    private static final String SERVICE = "service";
    private static final String CASE_ID = "123";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

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
}
