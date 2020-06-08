package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException.BadRequest;
import org.springframework.web.client.HttpClientErrorException.Forbidden;
import org.springframework.web.client.HttpClientErrorException.Unauthorized;
import org.springframework.web.client.HttpClientErrorException.UnprocessableEntity;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class CaseUpdateClientTest {

    private static final String UPDATE_CASE_URL = "/update-case";

    @Autowired
    private CaseUpdateClient client;

    @Value("${service-config.services[0].update-url}")
    private String updateUrl;

    @Test
    public void should_return_case_details_for_successful_update() throws Exception {

        // given
        String s2sToken = randomUUID().toString();

        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withRequestBody(matchingJsonPath("$.case_details.id", equalTo("23")))
                .withRequestBody(matchingJsonPath("$.case_details.case_type_id", equalTo("Bulk_Scanned")))
                .withRequestBody(matchingJsonPath("$.case_details.case_data", absent()))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(okJson(successResponse().toString()))
        );

        // when
        SuccessfulUpdateResponse response = client.updateCase(
            updateUrl,
            existingCase(),
            exceptionRecordRequestData(),
            s2sToken
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.warnings).isEmpty();
        CaseUpdateDetails caseDetails = response.caseDetails;
        assertThat(caseDetails).isNotNull();
        assertThat(caseDetails.eventId).isEqualTo("updateCase");
        assertThat(caseDetails.caseData).isNotNull();
    }

    @Test
    public void should_throw_unprocessable_entity_exception_for_unprocessable_entity_response() throws Exception {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(errorResponse().toString()).withStatus(422)));

        // when
        UnprocessableEntity exception = catchThrowableOfType(
            () -> client.updateCase(updateUrl, existingCase(), exceptionRecordRequestData(), s2sToken),
            UnprocessableEntity.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
        assertThat(exception.getStatusText()).isEqualTo("Unprocessable Entity");
        assertThat(exception.getResponseBodyAsString())
            .isEqualTo("{\"warnings\":[\"field2 is missing\"],\"errors\":[\"field1 is missing\"]}");
    }

    @Test
    public void should_throw_invalid_case_data_exception_for_bad_request() throws Exception {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(invalidDataResponse().toString()).withStatus(400)));

        // when
        BadRequest exception = catchThrowableOfType(
            () -> client.updateCase(updateUrl, existingCase(), exceptionRecordRequestData(), s2sToken),
            BadRequest.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(exception.getResponseBodyAsString()).isEqualTo("{\"errors\":[\"field1 is missing\"]}");
    }

    @Test
    public void should_throw_case_client_service_exception_when_unable_to_process_body() {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody("bad response".getBytes()).withStatus(400)));

        // when
        BadRequest exception = catchThrowableOfType(
            () -> client.updateCase(updateUrl, existingCase(), exceptionRecordRequestData(), s2sToken),
            BadRequest.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(exception.getResponseBodyAsString()).isEqualTo("bad response"); // because byte[]{}
    }

    @Test
    public void should_throw_exception_for_unauthorised_service_auth_header() {
        // given
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .willReturn(forbidden().withBody("Calling service is not authorised")));

        // when
        Forbidden exception = catchThrowableOfType(
            () -> client.updateCase(updateUrl, existingCase(), exceptionRecordRequestData(), randomUUID().toString()),
            Forbidden.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getResponseBodyAsString()).contains("Calling service is not authorised");
    }

    @Test
    public void should_throw_exception_for_invalid_service_auth_header() {
        // given
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .willReturn(unauthorized().withBody("Invalid S2S token")));

        // when
        Unauthorized exception = catchThrowableOfType(
            () -> client.updateCase(updateUrl, existingCase(), exceptionRecordRequestData(), randomUUID().toString()),
            Unauthorized.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getResponseBodyAsString()).isEqualTo("Invalid S2S token");
    }

    @Test
    public void should_throw_exception_for_server_exception() {
        // given
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .willReturn(serverError().withBody("Internal Server error")));

        // when
        InternalServerError exception = catchThrowableOfType(
            () -> client.updateCase(updateUrl, existingCase(), exceptionRecordRequestData(), randomUUID().toString()),
            InternalServerError.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getResponseBodyAsString()).contains("Internal Server error");
    }

    private CaseDetails existingCase() {
        return SampleData.THE_CASE;
    }

    private ExceptionRecord exceptionRecordRequestData() {
        return new ExceptionRecord(
            "id",
            "some_case_type",
            "poBox",
            "BULKSCAN",
            Classification.NEW_APPLICATION,
            "D8",
            now(),
            now(),
            singletonList(new ScannedDocument(
                DocumentType.CHERISHED,
                "D8",
                new DocumentUrl(
                    "http://locahost",
                    "http://locahost/binary",
                    "file1.pdf"
                ),
                "1234",
                "file1.pdf",
                now(),
                now()
            )),
            asList(
                new OcrDataField("name1", "value1"),
                new OcrDataField("name2", "value2")
            )
        );
    }

    private JSONObject successResponse() throws JSONException {
        return new JSONObject()
            .put("case_update_details", new JSONObject()
                .put("case_type_id", "some_case_type")
                .put("event_id", "updateCase")
                .put(
                    "case_data",
                    new JSONObject()
                        .put("case_field", "some value")
                        .put("form_type", "d8")
                ))
            .put("warnings", new JSONArray());
    }

    private JSONObject errorResponse() throws Exception {
        return new JSONObject()
            .put("errors", new JSONArray().put("field1 is missing"))
            .put("warnings", new JSONArray().put("field2 is missing"));
    }

    private JSONObject invalidDataResponse() throws Exception {
        return new JSONObject()
            .put("errors", new JSONArray().put("field1 is missing"));
    }
}
