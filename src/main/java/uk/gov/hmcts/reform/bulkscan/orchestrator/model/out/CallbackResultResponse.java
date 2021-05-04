package uk.gov.hmcts.reform.bulkscan.orchestrator.model.out;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.InstantSerializer;

import java.time.Instant;
import java.util.UUID;

public class CallbackResultResponse {
    @JsonProperty("id")
    public final UUID id;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonProperty("created_at")
    public final Instant createdAt;

    @JsonProperty("request_type")
    public final RequestType requestType;

    @JsonProperty("exception_record_id")
    public final String exceptionRecordId;

    @JsonProperty("case_id")
    public final String caseId;

    public CallbackResultResponse(CallbackResult callbackResult) {
        this.id = callbackResult.id;
        this.createdAt = callbackResult.createdAt;
        this.requestType = callbackResult.requestType;
        this.exceptionRecordId = callbackResult.exceptionRecordId;
        this.caseId = callbackResult.caseId;
    }
}
