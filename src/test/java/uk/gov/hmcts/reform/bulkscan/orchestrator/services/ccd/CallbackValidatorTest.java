package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackValidatorTest {
    private static final String SERVICE = "service";
    private static final String CASE_ID = "123";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    private CallbackValidator callbackValidator;

    @BeforeEach
    void setUp() {
        callbackValidator = new CallbackValidator();
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
}