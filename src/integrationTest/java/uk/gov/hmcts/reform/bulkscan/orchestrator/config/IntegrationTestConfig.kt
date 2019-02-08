package uk.gov.hmcts.reform.bulkscan.orchestrator.config

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.microsoft.azure.servicebus.IMessageHandler
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.util.SocketUtils.findAvailableTcpPort
import uk.gov.hmcts.reform.bulkscan.orchestrator.Application
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.MessageSender
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageOperations
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ProcessedEnvelopeNotifier
import java.lang.System.setProperty
import java.util.UUID


@Import(Application::class)
@Configuration
@Profile("integration", "nosb") // no servicebus queue handler registration
class IntegrationTestConfig : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(ctx: ConfigurableApplicationContext) {
        setProperty("wiremock.port", findAvailableTcpPort().toString())
    }

    @Bean
    fun options(@Value("\${wiremock.port}") port: Int): Options =
        WireMockConfiguration.options().port(port).notifier(Slf4jNotifier(false))

    @Bean
    fun messageSender(processor: IMessageHandler) = MessageSender(processor);

    @Bean
    fun messageOperations() = object : IMessageOperations {
        override fun complete(lockToken: UUID?) {
            // do nothing
        }

        override fun deadLetter(lockToken: UUID?, reason: String?, description: String?) {
            // do nothing
        }
    }

    @Bean
    fun processedEnvelopeNotifier() = mock(ProcessedEnvelopeNotifier::class.java)
}
