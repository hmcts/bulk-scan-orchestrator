package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest()
@ActiveProfiles("integration")
public class RootControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @Test
    public void call_to_root_endpoint_should_result_with_204_response() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isNoContent());
    }
}
