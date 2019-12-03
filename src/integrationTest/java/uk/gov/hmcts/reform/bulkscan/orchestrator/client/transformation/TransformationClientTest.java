package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import com.github.tomakehurst.wiremock.core.Options;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException.BadRequest;
import org.springframework.web.client.HttpClientErrorException.Forbidden;
import org.springframework.web.client.HttpClientErrorException.Unauthorized;
import org.springframework.web.client.HttpClientErrorException.UnprocessableEntity;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
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

@IntegrationTest
public class TransformationClientTest {

    private static final String TRANSFORM_EXCEPTION_RECORD_URL = "/transform-exception-record";

    @Autowired
    private TransformationClient client;

    @Autowired
    private Options wiremockOptions;

    @Test
    public void should_return_case_details_for_successful_transformation() throws Exception {

        // given
        String s2sToken = randomUUID().toString();

        stubFor(
            post(urlPathMatching(TRANSFORM_EXCEPTION_RECORD_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .withRequestBody(matchingJsonPath("scanned_documents[0].type", matching("[a-z]+")))
                .willReturn(okJson(successResponse().toString()))
        );

        // when
        SuccessfulTransformationResponse response = client.transformExceptionRecord(
            url(),
            exceptionRecordRequestData(),
            s2sToken
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.warnings).isEmpty();
        CaseCreationDetails caseCreationDetails = response.caseCreationDetails;
        assertThat(caseCreationDetails).isNotNull();
        assertThat(caseCreationDetails.caseTypeId).isEqualTo("some_case_type");
        assertThat(caseCreationDetails.eventId).isEqualTo("createCase");
        assertThat(caseCreationDetails.caseData).isNotNull();
    }

    @Test
    public void should_throw_unprocessable_entity_exception_for_unprocessable_entity_response() throws Exception {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(TRANSFORM_EXCEPTION_RECORD_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(errorResponse().toString()).withStatus(UNPROCESSABLE_ENTITY.value())));

        // when
        UnprocessableEntity exception = catchThrowableOfType(
            () -> client.transformExceptionRecord(url(), exceptionRecordRequestData(), s2sToken),
            UnprocessableEntity.class
        );

        // then
        assertThat(exception.getMessage()).isEqualTo("422 Unprocessable Entity");
        assertThat(exception.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
        assertThat(exception.getResponseBodyAsString())
            .isEqualTo("{\"warnings\":[\"field2 is missing\"],\"errors\":[\"field1 is missing\"]}");
    }

    @Test
    public void should_throw_invalid_case_data_exception_for_bad_request() throws Exception {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(TRANSFORM_EXCEPTION_RECORD_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(invalidDataResponse().toString()).withStatus(BAD_REQUEST.value())));

        // when
        BadRequest exception = catchThrowableOfType(
            () -> client.transformExceptionRecord(url(), exceptionRecordRequestData(), s2sToken),
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
            post(urlPathMatching(TRANSFORM_EXCEPTION_RECORD_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(new byte[]{}).withStatus(BAD_REQUEST.value())));

        // when
        BadRequest exception = catchThrowableOfType(
            () -> client.transformExceptionRecord(url(), exceptionRecordRequestData(), s2sToken),
            BadRequest.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(exception.getResponseBodyAsString()).isEqualTo(""); // because byte[]{}
    }

    @Test
    public void should_throw_exception_for_unauthorised_service_auth_header() {
        // given
        stubFor(
            post(urlPathMatching(TRANSFORM_EXCEPTION_RECORD_URL))
                .willReturn(forbidden().withBody("Calling service is not authorised")));

        // when
        Forbidden exception = catchThrowableOfType(
            () -> client.transformExceptionRecord(url(), exceptionRecordRequestData(), randomUUID().toString()),
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
            post(urlPathMatching(TRANSFORM_EXCEPTION_RECORD_URL))
                .willReturn(unauthorized().withBody("Invalid S2S token")));

        // when
        Unauthorized exception = catchThrowableOfType(
            () -> client.transformExceptionRecord(url(), exceptionRecordRequestData(), randomUUID().toString()),
            Unauthorized.class
        );

        // then
        assertThat(exception.getResponseBodyAsString()).isEqualTo("Invalid S2S token");
    }

    @Test
    public void should_throw_exception_for_server_exception() {
        // given
        stubFor(
            post(urlPathMatching(TRANSFORM_EXCEPTION_RECORD_URL))
                .willReturn(serverError().withBody("Internal Server error")));

        // when
        InternalServerError exception = catchThrowableOfType(
            () -> client.transformExceptionRecord(url(), exceptionRecordRequestData(), randomUUID().toString()),
            InternalServerError.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getResponseBodyAsString()).contains("Internal Server error");
    }

    private String url() {
        return "http://localhost:" + wiremockOptions.portNumber();
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
            .put("case_creation_details", new JSONObject()
                .put("case_type_id", "some_case_type")
                .put("event_id", "createCase")
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
