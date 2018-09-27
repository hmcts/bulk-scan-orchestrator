package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.NoUserConfiguredException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@RunWith(SpringRunner.class)
@Import(JurisdictionToUserMapping.class)
@EnableConfigurationProperties
@TestPropertySource(properties = {
    "idam.users.sscs.username=user@gmail.com",
    "idam.users.sscs.password=password"
})
public class JurisdictionToUserMappingTest {

    @Autowired
    JurisdictionToUserMapping mapping;

    @Test
    public void should_parse_up_the_properties_into_map() {
        Credential creds = mapping.getUser("SSCS");
        assertThat(creds.getPassword()).isEqualTo("password");
        assertThat(creds.getUsername()).isEqualTo("user@gmail.com");
    }

    @Test
    public void should_throw_exception_if_not_found() {
        Throwable throwable = catchThrowable(() -> mapping.getUser("NONE"));

        assertThat(throwable)
            .isInstanceOf(NoUserConfiguredException.class)
            .hasMessage("No user configured for jurisdiction: none");
    }
}
