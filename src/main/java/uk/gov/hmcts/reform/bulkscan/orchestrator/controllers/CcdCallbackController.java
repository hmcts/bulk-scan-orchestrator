package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AttachCaseCallbackService;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/callback", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class CcdCallbackController {

    private final AttachCaseCallbackService attachCaseCallbackService;

    public static final String USER_ID = "user-id";

    private static final CallbackResponse EMPTY_CALLBACK_ERROR_RESPONSE = AboutToStartOrSubmitCallbackResponse
        .builder()
        .errors(ImmutableList.of("Internal Error: callback or case details were empty"))
        .build();

    @Autowired
    public CcdCallbackController(AttachCaseCallbackService attachCaseCallbackService) {
        this.attachCaseCallbackService = attachCaseCallbackService;
    }

    @PostMapping(path = "/attach_case")
    public CallbackResponse attachToCase(
        @RequestBody CallbackRequest callback,
        @RequestHeader(value = "Authorization", required = false) String idamToken,
        @RequestHeader(value = USER_ID, required = false) String userId
    ) {
        if (callback != null && callback.getCaseDetails() != null) {

            return attachCaseCallbackService
                .process(callback.getCaseDetails(), idamToken, userId, callback.getEventId())
                .map(modifiedFields -> okResponse(modifiedFields))
                .getOrElseGet(errors -> errorResponse(errors));
        } else {
            return EMPTY_CALLBACK_ERROR_RESPONSE;
        }
    }

    private AboutToStartOrSubmitCallbackResponse okResponse(Map<String, Object> modifiedFields) {
        return AboutToStartOrSubmitCallbackResponse.builder().data(modifiedFields).errors(emptyList()).build();
    }

    private AboutToStartOrSubmitCallbackResponse errorResponse(List<String> errors) {
        return AboutToStartOrSubmitCallbackResponse.builder().errors(errors).build();
    }
}
