package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AttachCaseCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CreateCaseCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.ErrorsAndWarnings;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.ReclassifyCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

import java.util.Map;

import static java.util.Collections.emptyList;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/callback", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class CcdCallbackController {

    private final AttachCaseCallbackService attachCaseCallbackService;
    private final CreateCaseCallbackService createCaseCallbackService;
    private final ReclassifyCallbackService reclassifyCallbackService;

    public static final String USER_ID = "user-id";

    @Autowired
    public CcdCallbackController(
        AttachCaseCallbackService attachCaseCallbackService,
        CreateCaseCallbackService createCaseCallbackService,
        ReclassifyCallbackService reclassifyCallbackService
    ) {
        this.attachCaseCallbackService = attachCaseCallbackService;
        this.createCaseCallbackService = createCaseCallbackService;
        this.reclassifyCallbackService = reclassifyCallbackService;
    }

    @PostMapping(path = "/attach_case")
    public CallbackResponse attachToCase(
        @RequestBody CcdCallbackRequest callback,
        @RequestHeader(value = "Authorization", required = false) String idamToken,
        @RequestHeader(value = USER_ID, required = false) String userId
    ) {
        if (callback != null && callback.getCaseDetails() != null) {

            return attachCaseCallbackService
                .process(
                    callback.getCaseDetails(),
                    idamToken,
                    userId,
                    callback.getEventId(),
                    callback.isIgnoreWarnings()
                )
                .map(modifiedFields -> okResponse(modifiedFields))
                .getOrElseGet(errors -> errorResponse(errors));
        } else {
            throw new InvalidRequestException("Callback or case details were empty");
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
            throw new InvalidRequestException("Callback or case details were empty");
        }
    }

    @PostMapping(path = "/reclassify-exception-record")
    public CallbackResponse reclassifyExceptionRecord(
        @RequestBody CcdCallbackRequest callbackRequest,
        @RequestHeader(value = USER_ID, required = false) String userId
    ) {
        if (callbackRequest.getCaseDetails() == null) {
            throw new InvalidRequestException("Case details are missing in callback data");
        } else if (callbackRequest.getCaseDetails().getData() == null) {
            throw new InvalidRequestException("Case data is missing in case details");
        } else {
            ProcessResult result = reclassifyCallbackService.reclassifyExceptionRecord(
                callbackRequest.getCaseDetails(),
                userId
            );

            return AboutToStartOrSubmitCallbackResponse
                .builder()
                .data(result.getExceptionRecordData())
                .warnings(result.getWarnings())
                .errors(result.getErrors())
                .build();
        }
    }

    private AboutToStartOrSubmitCallbackResponse okResponse(Map<String, Object> modifiedFields) {
        return AboutToStartOrSubmitCallbackResponse.builder()
            .data(modifiedFields)
            .errors(emptyList())
            .warnings(emptyList())
            .build();
    }

    private AboutToStartOrSubmitCallbackResponse errorResponse(ErrorsAndWarnings errorsAndWarnings) {
        return AboutToStartOrSubmitCallbackResponse
            .builder()
            .errors(errorsAndWarnings.getErrors())
            .warnings(errorsAndWarnings.getWarnings())
            .build();
    }
}
