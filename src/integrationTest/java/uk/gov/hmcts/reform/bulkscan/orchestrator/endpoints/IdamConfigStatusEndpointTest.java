package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
class IdamConfigStatusEndpointTest {

    @Autowired
    private MockMvc mvc;

    @DisplayName("Should have BULKSCAN dummy jurisdiction configured properly")
    @Test
    void should_have_bulkscan_configured_properly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        byte[] response = mvc
            .perform(get("/idam-config-status"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        JsonNode responseNode = mapper.readTree(response);

        assertThat(responseNode.isArray()).isTrue();
        assertThat(ImmutableList.copyOf(responseNode.elements())).hasSize(1);

        JsonNode statusNode = responseNode.get(0);

        JurisdictionConfigurationStatus actual = new JurisdictionConfigurationStatus(
            statusNode.get("jurisdiction").asText(),
            statusNode.get("is_correct").asBoolean(),
            statusNode.get("error_description").isNull() ? null : statusNode.get("error_description").asText(),
            statusNode.get("error_response_status").isNull() ? null : statusNode.get("error_response_status").asInt()
        );

        assertThat(actual).isEqualToComparingFieldByField(
            new JurisdictionConfigurationStatus("bulkscan", true, null, null)
        );
    }
}
