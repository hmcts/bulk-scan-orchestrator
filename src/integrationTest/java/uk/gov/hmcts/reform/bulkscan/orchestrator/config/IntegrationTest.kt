package uk.gov.hmcts.reform.bulkscan.orchestrator.config

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

/**
 * ## Integration test annotation.
 * This annotation is meant for all the integration tests.
 * Its purpose is to:
 *  - centralise the integration test configuration
 *  - avoid copy and past
 *  - enable one profile for efficient running only @DirtysContext will restart/refresh the orchestrator context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock
@ActiveProfiles("integration","nosb") // no servicebus queue handler registration
@ContextConfiguration(initializers = [IntegrationTestInitializer::class])
internal annotation class IntegrationTest
