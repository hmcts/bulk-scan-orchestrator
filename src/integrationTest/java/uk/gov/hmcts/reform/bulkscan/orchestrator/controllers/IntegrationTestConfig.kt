package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.microsoft.azure.servicebus.IMessageReceiver
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ContextConfiguration
import org.springframework.util.SocketUtils
import org.springframework.util.SocketUtils.findAvailableTcpPort
import uk.gov.hmcts.reform.bulkscan.orchestrator.Application
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageReceiverFactory
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
    fun mockReciever(): IMessageReceiver = Mockito.mock(com.microsoft.azure.servicebus.IMessageReceiver::class.java)

    @Bean
    @Primary
    fun testProvider(mockReceiver: IMessageReceiver): MessageReceiverFactory = MessageReceiverFactory { mockReceiver }
}
