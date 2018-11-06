package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_REF
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_TYPE_BULK_SCAN
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.JURIDICTION
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.getCaseUrl
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi


@ExtendWith(SpringExtension::class)
@IntegrationTest
class CaseRetrievalTest {
    @Autowired
    private lateinit var server: WireMockServer

    @Autowired
    private lateinit var factory: CcdAuthenticatorFactory

    @Autowired
    private lateinit var coreCaseDataApi: CoreCaseDataApi

    private lateinit var caseRetriever: CaseRetriever

    @BeforeEach
    fun before() {
        caseRetriever = CaseRetriever(factory, coreCaseDataApi)
    }

    @Test
    fun `Should call to retrieve the case from ccd`() {
        caseRetriever.retrieve(JURIDICTION, CASE_REF)

        server.verify(getRequestedFor(urlEqualTo(getCaseUrl)))
    }
}
