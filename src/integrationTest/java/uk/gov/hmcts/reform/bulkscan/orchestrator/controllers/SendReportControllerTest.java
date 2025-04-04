package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureWireMock(port = 0)
@AutoConfigureMockMvc
@IntegrationTest
public class SendReportControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports/email-daily-report", "/reports/email-report?date=2025-03-11"})
    public void should_return_401_when_not_authenticated_with_api_key(String route) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(route))
            .andDo(print()).andExpect(status().isUnauthorized())
            .andReturn();

        Assertions.assertEquals("API Key is missing", mvcResult.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports/email-daily-report", "/reports/email-report?date=2025-03-11"})
    public void should_return_200_when_called_with_valid_api_key(String route) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-actions-api-key")
            )
            .andDo(print()).andExpect(status().isOk())
            .andReturn();

    }

    @Test
    public void should_return_400_when_called_with_invalid_date() throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post("/reports/email-report?date=abd")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-actions-api-key")
            )
            .andDo(print()).andExpect(status().isBadRequest())
            .andReturn();

    }

}
