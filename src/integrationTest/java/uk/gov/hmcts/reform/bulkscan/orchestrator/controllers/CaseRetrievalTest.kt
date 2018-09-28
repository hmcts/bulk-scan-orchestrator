package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.Application
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageReceiverProvider
import java.util.concurrent.CountDownLatch

val latch = CountDownLatch(1)

@Import(Application::class)
open class TestConfig {
    @Bean
    @Primary
    fun testProvider(): IMessageReceiverProvider {
        return IMessageReceiverProvider {
            try {
                throw RuntimeException("The right Provider")
            } finally {
                latch.countDown()
            }
        }
    }
}

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [TestConfig::class])
class CaseRetrievalTest {
    @Test
    fun `an initial test to check the wiring`() {
        latch.await()
    }
}
