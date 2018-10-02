package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import uk.gov.hmcts.reform.bulkscan.orchestrator.Application
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageReceiverFactory

@Import(Application::class)
open class TestConfig {
    @Bean
    fun options(): Options = WireMockConfiguration.options()
        //TODO we need to make this dynamic somehow. Maybe a RandomPropertySource ?
        .port(60222)
        //Change this to true if you need to debug
        .notifier(Slf4jNotifier(false))

    @Bean
    @Primary
    fun testProvider(): MessageReceiverFactory = MessageReceiverFactory { mockReciever }
}
