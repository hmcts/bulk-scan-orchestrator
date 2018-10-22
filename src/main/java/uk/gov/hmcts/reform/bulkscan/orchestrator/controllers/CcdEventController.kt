package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackProcessor
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse

@RestController
class CcdEventController(@Autowired val callbackProcessor: CallbackProcessor) {
    @PostMapping(
        value = ["/callback/{type}"],
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun handleCallback(
        @PathVariable("type") type: String,
        @RequestBody callback: CallbackRequest): ResponseEntity<CallbackResponse> {
        return ok().body(
            AboutToStartOrSubmitCallbackResponse
                .builder()
                .errors(callbackProcessor.processEventType(type, callback))
                .build()
        )
    }
}
