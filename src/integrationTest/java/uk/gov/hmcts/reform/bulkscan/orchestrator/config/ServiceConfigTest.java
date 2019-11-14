package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ServiceConfigTest {

    private static final String TEST_SERVICE_NAME = "bulkscan";

    @Autowired
    private ServiceConfigProvider serviceConfigProvider;

    @Test
    public void getCase_should_call_ccd_to_retrieve_the_case_by_ccd_id() {
        ServiceConfigItem configItem = serviceConfigProvider.getConfig(TEST_SERVICE_NAME);

        //form types and field names are defined in application-integration.yaml
        assertThat(configItem.getSurnameMapping("form1")).isEqualTo("lastName");
        assertThat(configItem.getSurnameMapping("form2")).isEqualTo("surname");
    }
}
