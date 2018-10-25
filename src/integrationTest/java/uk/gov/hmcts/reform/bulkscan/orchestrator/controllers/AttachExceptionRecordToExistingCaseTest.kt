package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType.JSON
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import u.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.PortWaiter.waitFor
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackTypes

@ExtendWith(SpringExtension::class)
@IntegrationTest
class AttachExceptionRecordToExistingCaseTest {

    @LocalServerPort
    private var applicationPort: Int = 0

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }


    private val request = CallbackRequest.builder().eventId(CallbackTypes.ABOUT_TO_SUBMIT)

    @Test
    fun `should successfully callback with correct information`() {
        given()
            .body(request.build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors.size()", equalTo(0))
    }
    @Test
    fun `should fail if invalid eventId set`() {
        given()
            .body(request.eventId("invalid").build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors",contains("Internal Error: event-id: invalid invalid"))
    }
    @Test
    fun `should fail if no eventId set`() {
        given()
            .body(request.eventId(null).build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors",contains("Internal Error: event-id: null invalid"))
    }
    @Test
    fun `should create error if type in incorrect`() {
        given()
            .body(request.build())
            .post("/callback/{type}", "someType")
            .then()
            .statusCode(200)
            .body("errors", contains("Internal Error: invalid type supplied: someType"))
    }
}
