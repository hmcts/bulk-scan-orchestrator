package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CaseCreationResultType;

public class CaseCreationResult {

    public final CaseCreationResultType resultType;
    public final Long caseCcdId;

    public CaseCreationResult(CaseCreationResultType resultType, Long caseCcdId) {
        this.resultType = resultType;
        this.caseCcdId = caseCcdId;
    }

    public static CaseCreationResult createdCase(Long caseCcdId) {
        return new CaseCreationResult(CaseCreationResultType.CREATED_CASE, caseCcdId);
    }

    public static CaseCreationResult caseAlreadyExists(Long caseCcdId) {
        return new CaseCreationResult(CaseCreationResultType.CASE_ALREADY_EXISTS, caseCcdId);
    }

    public static CaseCreationResult unrecoverableFailure() {
        return new CaseCreationResult(CaseCreationResultType.UNRECOVERABLE_FAILURE, null);
    }

    public static CaseCreationResult potentiallyRecoverableFailure() {
        return new CaseCreationResult(CaseCreationResultType.POTENTIALLY_RECOVERABLE_FAILURE, null);
    }

    public static CaseCreationResult abortedWithoutFailure() {
        return new CaseCreationResult(CaseCreationResultType.ABORTED_WITHOUT_FAILURE, null);
    }
}
