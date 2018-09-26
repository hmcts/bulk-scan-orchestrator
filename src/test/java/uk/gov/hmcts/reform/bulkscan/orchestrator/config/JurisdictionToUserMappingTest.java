package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
public class JurisdictionToUserMappingTest {

    @Autowired
    JurisdictionToUserMapping mapping;

    @Test
    public void should_parse_up_the_properties_into_map() {
        Credential creds = mapping.getUser("SSCS");
        assertThat(creds.getPassword()).isEqualTo("Password12");
        assertThat(creds.getUsername()).isEqualTo("bulkscanorchestrator+systemupdate@gmail.com");
    }
}
