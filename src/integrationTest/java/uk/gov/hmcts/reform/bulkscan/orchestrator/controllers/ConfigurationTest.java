package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
public class ConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    public void should_not_load_the_queue_client_for_envelopes() {
        assertThat(context.containsBean("envelopes")).isFalse();
    }
}
