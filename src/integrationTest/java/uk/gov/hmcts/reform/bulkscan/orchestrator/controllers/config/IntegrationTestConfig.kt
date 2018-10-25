package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.microsoft.azure.servicebus.IMessageHandler
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
import java.lang.System.setProperty

@Import(Application::class)
@Configuration
@Profile("integration")
class IntegrationTestConfig : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(ctx: ConfigurableApplicationContext) {
        setProperty("wiremock.port", findAvailableTcpPort().toString())
    }

    @Bean
    fun options(@Value("\${wiremock.port}") port: Int): Options =
        WireMockConfiguration.options().port(port).notifier(Slf4jNotifier(false))

    @Bean
    fun messageSender(processor: IMessageHandler) = MessageSender(processor);

}
