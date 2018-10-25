package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.CallbackProcessor;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class CcdCallbackController {

    private final CallbackProcessor callbackProcessor;

    @Autowired
    public CcdCallbackController(CallbackProcessor callbackProcessor) {
        this.callbackProcessor = callbackProcessor;
    }

    @PostMapping(
        path = "/callback/{type}",
        consumes = APPLICATION_JSON_VALUE,
        produces = APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CallbackResponse> handleCallback(
        @PathVariable("type") String type,
        @RequestBody CallbackRequest callback) {
        return ok().body(
            AboutToStartOrSubmitCallbackResponse
                .builder()
                .errors(callbackProcessor.process(type,callback.getEventId(),callback.getCaseDetails()))
                .build()
        );
    }
}
