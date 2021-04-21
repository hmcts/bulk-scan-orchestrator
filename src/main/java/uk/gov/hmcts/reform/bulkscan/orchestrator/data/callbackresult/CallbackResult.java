package uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult;

import java.time.Instant;
import java.util.UUID;

public class CallbackResult {
    public final UUID id;
    public final Instant createdAt;
    public final RequestType requestType;
    public final String exceptionRecordId;
    public final String caseId;

    public CallbackResult(
        UUID id,
        Instant createdAt,
        RequestType requestType,
        String exceptionRecordId,
        String caseId
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.requestType = requestType;
        this.exceptionRecordId = exceptionRecordId;
        this.caseId = caseId;
    }
}
