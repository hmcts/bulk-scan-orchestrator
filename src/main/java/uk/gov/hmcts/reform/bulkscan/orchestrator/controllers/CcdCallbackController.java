package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AttachCaseCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CreateCaseCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/callback", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class CcdCallbackController {

    private final AttachCaseCallbackService attachCaseCallbackService;
    private final CreateCaseCallbackService createCaseCallbackService;

    public static final String USER_ID = "user-id";

    @Autowired
    public CcdCallbackController(
        AttachCaseCallbackService attachCaseCallbackService,
        CreateCaseCallbackService createCaseCallbackService
    ) {
        this.attachCaseCallbackService = attachCaseCallbackService;
        this.createCaseCallbackService = createCaseCallbackService;
    }

    @PostMapping(path = "/attach_case")
    public CallbackResponse attachToCase(
        @RequestBody CcdCallbackRequest callbackRequest,
        @RequestHeader(value = "Authorization", required = false) String idamToken,
        @RequestHeader(value = USER_ID, required = false) String userId
    ) {
        if (callbackRequest != null && callbackRequest.getCaseDetails() != null) {

            return attachCaseCallbackService
                .process(callbackRequest, idamToken, userId)
                .map(modifiedFields -> okResponse(modifiedFields))
                .getOrElseGet(errors -> errorResponse(errors));
        } else {
            return errorResponse(ImmutableList.of("Internal Error: callback or case details were empty"));
        }
    }

    @PostMapping(path = "/create-new-case")
    public CallbackResponse createCase(
        @RequestBody CcdCallbackRequest callbackRequest,
        @RequestHeader(value = "Authorization", required = false) String idamToken,
        @RequestHeader(value = USER_ID, required = false) String userId
    ) {
        if (callbackRequest != null && callbackRequest.getCaseDetails() != null) {
            ProcessResult result = createCaseCallbackService.process(callbackRequest, idamToken, userId);

            return AboutToStartOrSubmitCallbackResponse
                .builder()
                .data(result.getExceptionRecordData())
                .warnings(result.getWarnings())
                .errors(result.getErrors())
                .build();
        } else {
            return errorResponse(ImmutableList.of("Internal Error: callback or case details were empty"));
        }
    }

    private AboutToStartOrSubmitCallbackResponse okResponse(Map<String, Object> modifiedFields) {
        return AboutToStartOrSubmitCallbackResponse.builder().data(modifiedFields).errors(emptyList()).build();
    }

    private AboutToStartOrSubmitCallbackResponse errorResponse(List<String> errors) {
        return AboutToStartOrSubmitCallbackResponse.builder().errors(errors).build();
    }
}
