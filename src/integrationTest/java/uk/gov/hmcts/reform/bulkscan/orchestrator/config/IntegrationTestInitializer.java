package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageOperations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.MessageSender;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.util.SocketUtils.findAvailableTcpPort;

@Configuration
@Profile({"integration", "nosb"}) // no servicebus queue handler registration
class IntegrationTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        System.setProperty("wiremock.port", Integer.toString(findAvailableTcpPort()));
    }

    @Bean
    public Options options(@Value("${wiremock.port}") int port) {
        return WireMockConfiguration.options().port(port).notifier(new Slf4jNotifier(false));
    }

    @Bean
    public MessageSender messageSender(IMessageHandler processor) {
        return new MessageSender(processor);
    }

    @Bean
    public IMessageOperations messageOperations() {
        return new IMessageOperations() {
            @Override
            public void complete(UUID lockToken) {
                // do nothing
            }

            @Override
            public void deadLetter(UUID lockToken, String reason, String description) {
                // do nothing
            }
        };
    }

    @Bean
    public ProcessedEnvelopeNotifier processedEnvelopeNotifier() {
        return mock(ProcessedEnvelopeNotifier.class);
    }
}
