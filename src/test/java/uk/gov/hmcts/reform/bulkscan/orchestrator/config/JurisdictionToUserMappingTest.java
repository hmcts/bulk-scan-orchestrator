package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@Import(JurisdictionToUserMapping.class)
@EnableConfigurationProperties
@TestPropertySource(properties = {
    "idam.users.sscs.username=bulkscanorchestrator+systemupdate@gmail.com",
    "idam.users.sscs.password=Password12"
})
public class JurisdictionToUserMappingTest {

    @Autowired
    JurisdictionToUserMapping mapping;

    @Test
    public void should_parse_up_the_properties_into_map() {
        Credential creds = mapping.getUser("SSCS");
        assertThat(creds.getPassword()).isEqualTo("Password12");
        assertThat(creds.getUsername()).isEqualTo("bulkscanorchestrator+systemupdate@gmail.com");
    }

    @Test
    public void should_return_empty_optional_if_not_found() {
        Throwable throwable = Assertions.catchThrowable(() -> mapping.getUser("NONE"));
        assertThat(throwable).isInstanceOf(UserNotConfiguredException.class);
        assertThat(throwable).hasMessage("No configured user for jurisdiction:NONE found");
    }

    private Credential failFound() {
        Assert.fail("Found creds when there should be none");
        return null;
    }

    private Credential failNotFound() {
        Assert.fail("Did not find the SSCS map item");
        return null;
    }
}
