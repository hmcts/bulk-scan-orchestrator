package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AttachCaseCallbackService;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

import java.util.List;
import javax.validation.constraints.NotNull;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class CcdCallbackController {

    private final AttachCaseCallbackService attachCaseCallbackService;

    @Autowired
    public CcdCallbackController(AttachCaseCallbackService attachCaseCallbackService) {
        this.attachCaseCallbackService = attachCaseCallbackService;
    }

    @PostMapping(
        path = "/callback/attach_case",
        consumes = APPLICATION_JSON_VALUE,
        produces = APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CallbackResponse> handleCallback(@RequestBody CallbackRequest callback) {
        if (callback != null && callback.getCaseDetails() != null) {
            return respondWithErrorList(attachCaseCallbackService.process(callback.getCaseDetails()));
        } else {
            return respondWithErrorList(ImmutableList.of("Internal Error: callback or case details were empty"));
        }
    }

    @NotNull
    private ResponseEntity<CallbackResponse> respondWithErrorList(List<String> errors) {
        return ok()
            .body(AboutToStartOrSubmitCallbackResponse
                .builder()
                .errors(errors)
                .build());
    }
}

