package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CaseCreationResultType;

import static org.assertj.core.api.Assertions.assertThat;

class CaseCreationResultTest {

    @Test
    void createdCase_returns_the_right_result() {
        long caseId = 1234L;

        var result = CaseCreationResult.createdCase(caseId);

        assertThat(result.resultType).isEqualTo(CaseCreationResultType.CREATED_CASE);
        assertThat(result.caseCcdId).isEqualTo(caseId);
    }

    @Test
    void caseAlreadyExists_returns_the_right_result() {
        long caseId = 1234L;

        var result = CaseCreationResult.caseAlreadyExists(caseId);

        assertThat(result.resultType).isEqualTo(CaseCreationResultType.CASE_ALREADY_EXISTS);
        assertThat(result.caseCcdId).isEqualTo(caseId);
    }

    @Test
    void unrecoverableFailure_returns_the_right_result() {
        var result = CaseCreationResult.abortedWithoutFailure();

        assertThat(result.resultType).isEqualTo(CaseCreationResultType.ABORTED_WITHOUT_FAILURE);
        assertThat(result.caseCcdId).isNull();
    }

    @Test
    void potentiallyRecoverableFailure_returns_the_right_result() {
        var result = CaseCreationResult.potentiallyRecoverableFailure();

        assertThat(result.resultType).isEqualTo(CaseCreationResultType.POTENTIALLY_RECOVERABLE_FAILURE);
        assertThat(result.caseCcdId).isNull();
    }

    @Test
    void abortWithoutFailure_returns_the_right_result() {
        var result = CaseCreationResult.unrecoverableFailure();

        assertThat(result.resultType).isEqualTo(CaseCreationResultType.UNRECOVERABLE_FAILURE);
        assertThat(result.caseCcdId).isNull();
    }
}
