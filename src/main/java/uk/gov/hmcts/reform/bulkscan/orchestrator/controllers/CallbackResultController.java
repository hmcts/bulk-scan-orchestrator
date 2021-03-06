package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.CallbackResultListResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.CallbackResultResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackResultService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/callback-results", produces = APPLICATION_JSON_VALUE)
public class CallbackResultController {
    private static final String CASE_ID = "case_id";
    private static final String EXCEPTION_RECORD_ID = "exception_record_id";

    private final CallbackResultService callbackResultService;

    public CallbackResultController(CallbackResultService callbackResultService) {
        this.callbackResultService = callbackResultService;
    }

    @GetMapping
    @ApiOperation(
        value = "Retrieves callback results",
        notes = "Returns an empty list when no callback results were found"
    )
    @ApiResponse(code = 200, message = "Success", response = CallbackResultListResponse.class)
    public CallbackResultListResponse getCallbackResults(@RequestParam Map<String, String> filters) {
        if (filters.size() == 1) {
            if (filters.containsKey(CASE_ID) && !filters.get(CASE_ID).isEmpty()) {
                List<CallbackResult> callbackResults = callbackResultService.findByCaseId(filters.get(CASE_ID));
                return getResponse(callbackResults);
            }

            if (filters.containsKey(EXCEPTION_RECORD_ID) && !filters.get(EXCEPTION_RECORD_ID).isEmpty()) {
                List<CallbackResult> callbackResults =
                    callbackResultService.findByExceptionRecordId(filters.get(EXCEPTION_RECORD_ID));
                return getResponse(callbackResults);
            }
        }

        throw new InvalidRequestException("Request should have exactly one parameter '"
            + CASE_ID + "' or '" + EXCEPTION_RECORD_ID + "'");
    }

    private CallbackResultListResponse getResponse(List<CallbackResult> callbackResults) {
        List<CallbackResultResponse> callbackResultResponses = callbackResults
            .stream()
            .map(CallbackResultResponse::new)
            .collect(Collectors.toList());
        return new CallbackResultListResponse(callbackResultResponses);
    }
}
