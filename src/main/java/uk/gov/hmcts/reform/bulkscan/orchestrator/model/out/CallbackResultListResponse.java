package uk.gov.hmcts.reform.bulkscan.orchestrator.model.out;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CallbackResultListResponse {

    @JsonProperty("count")
    public final int count;

    @JsonProperty("callback-results")
    public final List<CallbackResultResponse> callbackResults;

    @JsonCreator
    public CallbackResultListResponse(
        @JsonProperty("callback-results") List<CallbackResultResponse> callbackResults
    ) {
        this.count = callbackResults.size();
        this.callbackResults = callbackResults;
    }
}
