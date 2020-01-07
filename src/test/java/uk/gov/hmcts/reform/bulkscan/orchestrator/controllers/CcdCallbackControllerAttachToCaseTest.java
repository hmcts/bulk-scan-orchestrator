package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AttachCaseCallbackService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CreateCaseCallbackService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CcdCallbackController.class)
class CcdCallbackControllerAttachToCaseTest {

    @Autowired MockMvc mvc;

    @MockBean AttachCaseCallbackService attachService;
    @MockBean CreateCaseCallbackService createService;

    @Test
    void should_return_500_in_case_of_internal_error() throws Exception {

        given(attachService.process(any(), any(), any(), any(), anyBoolean()))
            .willThrow(new RuntimeException("Some internal unhandled exception"));

        mvc
            .perform(
                post("/callback/attach_case")
                    .content("{ 'case_details': { 'id' : 123 }, 'event_id': '' }".replace("'", "\""))
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isInternalServerError());
    }

    @Test
    void should_return_400_when_case_details_are_missing() throws Exception {
        mvc
            .perform(
                post("/callback/attach_case")
                    .content("{ 'event_id': 'attach_to_case' }".replace("'", "\""))
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest());
    }
}
