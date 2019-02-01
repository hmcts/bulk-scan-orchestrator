package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints.IdamConfigStatusEndpoint;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
public class ConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    public void should_load_custom_actuator_endpoint_for_jurisdictions() {
        String clazz = IdamConfigStatusEndpoint.class.getSimpleName();

        assertThat(context.containsBean(clazz.substring(0, 1).toLowerCase() + clazz.substring(1))).isTrue();
    }
}
