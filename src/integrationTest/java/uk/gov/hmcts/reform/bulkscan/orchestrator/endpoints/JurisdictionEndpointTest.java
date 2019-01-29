package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest;

import java.util.AbstractMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
@AutoConfigureMockMvc
public class JurisdictionEndpointTest {

    @Autowired
    private MockMvc mvc;

    @DisplayName("Should have BULKSCAN dummy jurisdiction configured properly")
    @Test
    public void should_have_bulkscan_configured_properly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        byte[] response = mvc
            .perform(get("/jurisdictions"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        Map<String, HttpStatus> parsedResponse = mapper.readValue(
            response,
            new TypeReference<Map<String, HttpStatus>>() {}
        );

        assertThat(parsedResponse)
            .hasSize(1)
            .containsExactly(new AbstractMap.SimpleEntry<>("bulkscan", HttpStatus.OK));
    }
}
