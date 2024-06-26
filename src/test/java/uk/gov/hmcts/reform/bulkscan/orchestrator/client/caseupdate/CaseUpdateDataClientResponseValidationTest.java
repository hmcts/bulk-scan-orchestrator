package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import com.google.common.collect.ImmutableMap;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExistingCaseDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CaseUpdateDataClientResponseValidationTest {

    @Mock RestTemplate restTemplate;

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    CaseUpdateDataClient client;

    @BeforeEach
    void setUp() {
        this.client = new CaseUpdateDataClient(validator, restTemplate);
    }

    @Test
    void should_require_not_null_caseDetails() {
        // given
        serverRespondsWith(
            new SuccessfulUpdateResponse(
                null,
                emptyList()
            )
        );

        // then
        expectViolations(
            tuple("caseDetails", "must not be null")
        );
    }

    @Test
    void should_require_valid_caseDetails() {
        // given
        serverRespondsWith(
            new SuccessfulUpdateResponse(
                new CaseUpdateDetails(
                    "",
                    null
                ),
                emptyList()
            )
        );

        // then
        expectViolations(
            tuple("caseDetails.caseData", "must not be null")
        );
    }

    @Test
    void should_not_throw_exception_for_valid_response() {
        // given
        serverRespondsWith(
            new SuccessfulUpdateResponse(
                new CaseUpdateDetails(
                    null,
                    ImmutableMap.of("key", "value")
                ),
                emptyList()
            )
        );
        // then
        assertThatCode(() -> callUpdateCase())
            .doesNotThrowAnyException();
    }

    void expectViolations(Tuple... violations) {
        assertThatExceptionOfType(ConstraintViolationException.class)
            .isThrownBy(() -> callUpdateCase())
            .satisfies(exc ->
                assertThat(exc.getConstraintViolations())
                    .extracting(violation -> tuple(violation.getPropertyPath().toString(), violation.getMessage()))
                    .containsExactlyInAnyOrder(violations)
            );
    }

    void serverRespondsWith(SuccessfulUpdateResponse response) {
        given(restTemplate.postForObject(anyString(), any(), any()))
            .willReturn(response);
    }

    void callUpdateCase() {
        client.getCaseUpdateData(
            "http://some-url.com/update",
            "s2s-token",
            new CaseUpdateRequest(
                mock(ExceptionRecord.class),
                false,
                mock(uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateDetails.class),
                mock(ExistingCaseDetails.class)
            )
        );
    }

}
