package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.ProcessedEnvelopeNotifier;

import static org.mockito.Mockito.mock;

@Configuration
@Profile("nosb") // no servicebus queue handler registration
class IntegrationTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // setting for substitution used in properties file.
        // wiremock will override this value if test specifies as "wiremock test"
        System.setProperty("wiremock.server.port", "8080");
    }

    @Bean
    public ProcessedEnvelopeNotifier processedEnvelopeNotifier() {
        return mock(ProcessedEnvelopeNotifier.class);
    }

    @Bean
    public PaymentsPublisher paymentsPublisher() {
        return mock(PaymentsPublisher.class);
    }

    @Bean
    public ServiceBusProcessorClient envelopesMessageReceiver() {
        return mock(ServiceBusProcessorClient.class);
    }

    @Bean("envelopes-dead-letter-send")
    public ServiceBusSenderClient envelopesDeadLetterSend() {
        return mock(ServiceBusSenderClient.class);
    }
}
