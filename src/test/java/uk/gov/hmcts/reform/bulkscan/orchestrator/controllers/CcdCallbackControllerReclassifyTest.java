package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AttachCaseCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CreateCaseCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.ReclassifyCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CcdCallbackController.class)
public class CcdCallbackControllerReclassifyTest {

    private static final String VALID_REQUEST_BODY =
        jsonString("{ 'case_details': { 'id' : 123, 'data': {} }, 'event_id': '' }");

    @Autowired
    MockMvc mvc;

    @MockBean
    AttachCaseCallbackService attachService;
    @MockBean
    CreateCaseCallbackService createService;
    @MockBean
    ReclassifyCallbackService reclassifyCallbackService;

    @Test
    void should_return_updated_exception_record_when_processing_is_successful() throws Exception {
        given(reclassifyCallbackService.reclassifyExceptionRecord(any(), any()))
            .willReturn(new ProcessResult(ImmutableMap.of("field1", "value1")));

        callReclassifyEndpoint(VALID_REQUEST_BODY)
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().json("{'errors':[],'warnings':[],'data':{'field1':'value1'}}"));
    }

    @Test
    void should_return_500_response_when_reclassify_callback_service_fails() throws Exception {
        willThrow(new RuntimeException("test exception"))
            .given(reclassifyCallbackService)
            .reclassifyExceptionRecord(any(), any());

        callReclassifyEndpoint(VALID_REQUEST_BODY)
            .andExpect(status().isInternalServerError());
    }

    @Test
    void should_return_400_response_when_case_details_are_missing() throws Exception {
        String bodyWithoutCaseDetails = jsonString("{ 'event_id': 'attach_to_case' }");

        callReclassifyEndpoint(bodyWithoutCaseDetails)
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{'message':'Case details are missing in callback data'}"));
    }

    @Test
    void should_return_400_response_when_exception_record_fields_are_missing() throws Exception {
        String bodyWithoutCaseData = jsonString("{ 'case_details': { 'id' : 123 }, 'event_id': '' }");

        callReclassifyEndpoint(bodyWithoutCaseData)
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{'message':'Case data is missing in case details'}"));
    }

    @Test
    void should_return_400_response_when_no_body_provided() throws Exception {
        callReclassifyEndpoint("").andExpect(status().isBadRequest());
    }

    @Test
    void should_reutrn_400_response_when_body_is_malformed() throws Exception {
        callReclassifyEndpoint("malformed body").andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_response_when_user_id_contains_invalid_characters() throws Exception {
        callReclassifyEndpoint(VALID_REQUEST_BODY, "user\tId\tWith\tInvalid\tCharacters")
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{'message':'User ID contains invalid characters'}"));
    }

    private static String jsonString(String singleQuotedString) {
        return singleQuotedString.replace("'", "\"");
    }

    private ResultActions callReclassifyEndpoint(String body) throws Exception {
        return callReclassifyEndpoint(body, "userId1");
    }

    private ResultActions callReclassifyEndpoint(String body, String userId) throws Exception {
        return mvc.perform(
            post("/callback/reclassify-exception-record")
                .content(body)
                .header("user-id", userId)
                .contentType(MediaType.APPLICATION_JSON)
        );
    }
}
