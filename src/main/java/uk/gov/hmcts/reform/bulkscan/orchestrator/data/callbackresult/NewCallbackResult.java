package uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.CREATE_CASE;

public class NewCallbackResult {
    public final RequestType requestType;
    public final String exceptionRecordId;
    public final String caseId;

    public static NewCallbackResult createCaseRequest(String exceptionRecordId, String caseId) {
        return new NewCallbackResult(CREATE_CASE, exceptionRecordId, caseId);
    };

    public static NewCallbackResult attachToCaseCaseRequest(String exceptionRecordId, String caseId) {
        return new NewCallbackResult(ATTACH_TO_CASE, exceptionRecordId, caseId);
    };

    private NewCallbackResult(
        RequestType requestType,
        String exceptionRecordId,
        String caseId
    ) {
        this.requestType = requestType;
        this.exceptionRecordId = exceptionRecordId;
        this.caseId = caseId;
    }
}
