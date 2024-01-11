package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.launchdarkly.LaunchDarklyClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.launchdarkly.LaunchDarklyClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource("classpath:application.conf")
@ExtendWith(SpringExtension.class)
class LaunchDarklySmokeTest {

    @MockBean
    private LaunchDarklyClient ldClient;
    @MockBean
    private LaunchDarklyClientFactory ldFactory;

    @Value("${sdk-key:YYYYY}")
    private String sdkKey;

    @Value("${offline-mode:false}")
    private Boolean offlineMode;

    @BeforeEach
    void setUp() {
        ldFactory = new LaunchDarklyClientFactory();
        ldClient = new LaunchDarklyClient(ldFactory, sdkKey, offlineMode);
    }

    @Test
    void checkLaunchDarklyStatus() {
        DataSourceStatusProvider.Status ldStatus = ldClient.getDataSourceStatus();
        assertThat(ldStatus.getState()).isEqualTo(DataSourceStatusProvider.State.VALID);
    }
}
