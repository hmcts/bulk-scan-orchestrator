package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AttachCaseCallbackService;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/callback", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class CcdCallbackController {

    private final AttachCaseCallbackService attachCaseCallbackService;

    @Autowired
    public CcdCallbackController(AttachCaseCallbackService attachCaseCallbackService) {
        this.attachCaseCallbackService = attachCaseCallbackService;
    }

    @PostMapping(path = "/attach_case")
    public CallbackResponse attachToCase(@RequestBody CallbackRequest callback) {
        if (callback != null && callback.getCaseDetails() != null) {
            List<String> errors = attachCaseCallbackService.process(callback.getCaseDetails());
            return response(errors);
        } else {
            return response(ImmutableList.of("Internal Error: callback or case details were empty"));
        }
    }

    private AboutToStartOrSubmitCallbackResponse response(List<String> errors) {
        return AboutToStartOrSubmitCallbackResponse.builder().errors(errors).build();
    }
}

