package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
@AutoConfigureMockMvc
public class IdamConfigStatusEndpointTest {

    @Autowired
    private MockMvc mvc;

    @DisplayName("Should have BULKSCAN dummy jurisdiction configured properly")
    @Test
    public void should_have_bulkscan_configured_properly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        byte[] response = mvc
            .perform(get("/idam-config-status"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        JsonNode responseNode = mapper.readTree(response);

        assertThat(responseNode.isArray()).isTrue();
        assertThat(responseNode.elements()).hasSize(1);

        JurisdictionConfigurationStatus actual = new JurisdictionConfigurationStatus(
            responseNode.get(0).get("jurisdiction").asText(),
            responseNode.get(0).get("is_correct").asBoolean(),
            responseNode.get(0).get("error_description").isNull() ? null : "must be null - failure"
        );

        assertThat(actual).isEqualToComparingFieldByField(
            new JurisdictionConfigurationStatus("bulkscan", true)
        );
    }
}
