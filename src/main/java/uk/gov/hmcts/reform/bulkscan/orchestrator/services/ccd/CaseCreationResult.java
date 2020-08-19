package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CaseCreationResultType;

public final class CaseCreationResult {

    private static final CaseCreationResult UNRECOVERABLE_FAILURE_RESULT =
        new CaseCreationResult(CaseCreationResultType.UNRECOVERABLE_FAILURE, null);

    private static final CaseCreationResult POTENTIALLY_RECOVERABLE_FAILURE_RESULT =
        new CaseCreationResult(CaseCreationResultType.POTENTIALLY_RECOVERABLE_FAILURE, null);

    private static final CaseCreationResult ABORTED_WITHOUT_FAILURE_RESULT =
        new CaseCreationResult(CaseCreationResultType.ABORTED_WITHOUT_FAILURE, null);

    public final CaseCreationResultType resultType;
    public final Long caseCcdId;

    private CaseCreationResult(CaseCreationResultType resultType, Long caseCcdId) {
        this.resultType = resultType;
        this.caseCcdId = caseCcdId;
    }

    public static CaseCreationResult caseCreated(Long caseCcdId) {
        return new CaseCreationResult(CaseCreationResultType.CASE_CREATED, caseCcdId);
    }

    public static CaseCreationResult caseAlreadyExists(Long caseCcdId) {
        return new CaseCreationResult(CaseCreationResultType.CASE_ALREADY_EXISTS, caseCcdId);
    }

    public static CaseCreationResult unrecoverableFailure() {
        return UNRECOVERABLE_FAILURE_RESULT;
    }

    public static CaseCreationResult potentiallyRecoverableFailure() {
        return POTENTIALLY_RECOVERABLE_FAILURE_RESULT;
    }

    public static CaseCreationResult abortedWithoutFailure() {
        return ABORTED_WITHOUT_FAILURE_RESULT;
    }
}
