package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResultType.ABORTED_WITHOUT_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResultType.CASE_ALREADY_EXISTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResultType.CASE_CREATED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationResultType.UNRECOVERABLE_FAILURE;

public final class CaseCreationResult {

    public final CaseCreationResultType resultType;
    public final Long caseCcdId;

    private CaseCreationResult(CaseCreationResultType resultType, Long caseCcdId) {
        this.resultType = resultType;
        this.caseCcdId = caseCcdId;
    }

    public static CaseCreationResult caseCreated(Long caseCcdId) {
        return new CaseCreationResult(CASE_CREATED, caseCcdId);
    }

    public static CaseCreationResult caseAlreadyExists(Long caseCcdId) {
        return new CaseCreationResult(CASE_ALREADY_EXISTS, caseCcdId);
    }

    public static CaseCreationResult unrecoverableFailure() {
        return new CaseCreationResult(UNRECOVERABLE_FAILURE, null);
    }

    public static CaseCreationResult potentiallyRecoverableFailure() {
        return new CaseCreationResult(POTENTIALLY_RECOVERABLE_FAILURE, null);
    }

    public static CaseCreationResult abortedWithoutFailure() {
        return new CaseCreationResult(ABORTED_WITHOUT_FAILURE, null);
    }
}
