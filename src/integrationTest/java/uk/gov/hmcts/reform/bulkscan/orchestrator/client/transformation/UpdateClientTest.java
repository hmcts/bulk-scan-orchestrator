package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import com.github.tomakehurst.wiremock.core.Options;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@IntegrationTest
public class UpdateClientTest {

    private static final String UPDATE_CASE_URL = "/update-case";

    @Autowired
    private UpdateClient client;

    @Autowired
    private Options wiremockOptions;

    @Test
    public void should_return_case_details_for_successful_update() throws Exception {

        // given
        String s2sToken = randomUUID().toString();

        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(okJson(successResponse().toString()))
        );

        // when
        SuccessfulUpdateResponse response = client.updateCase(
            url(),
            existingCase(),
            exceptionRecordRequestData(),
            s2sToken
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.warnings).isEmpty();
        CaseUpdateDetails caseDetails = response.caseDetails;
        assertThat(caseDetails).isNotNull();
        assertThat(caseDetails.caseData).isNotNull();
    }

    @Test
    public void should_throw_invalid_data_exception_for_unprocessable_entity_response() throws Exception {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(errorResponse().toString()).withStatus(422)));

        // when
        Throwable throwable = catchThrowable(
            () -> client.updateCase(url(), existingCase(), exceptionRecordRequestData(), s2sToken)
        );

        // then
        assertThat(throwable).isInstanceOf(InvalidCaseDataException.class);

        // and
        InvalidCaseDataException exception = (InvalidCaseDataException) throwable;

        assertThat(exception.getStatus()).isEqualTo(UNPROCESSABLE_ENTITY);
        assertThat(exception.getResponse()).isNotNull();
        assertThat(exception.getResponse().errors).isNotEmpty();
        assertThat(exception.getResponse().warnings).isNotEmpty();
    }

    @Test
    public void should_throw_invalid_data_exception_for_bad_request() throws Exception {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(invalidDataResponse().toString()).withStatus(400)));

        // when
        Throwable throwable = catchThrowable(
            () -> client.updateCase(url(), existingCase(), exceptionRecordRequestData(), s2sToken)
        );

        // then
        assertThat(throwable).isInstanceOf(InvalidCaseDataException.class);

        // and
        InvalidCaseDataException exception = (InvalidCaseDataException) throwable;

        assertThat(exception.getStatus()).isEqualTo(BAD_REQUEST);
        assertThat(exception.getResponse()).isNotNull();
        assertThat(exception.getResponse().errors).isNotEmpty();
        assertThat(exception.getResponse().warnings).isNull();
    }

    @Test
    public void should_throw_case_transformation_exception_when_unable_to_process_body() {
        // given
        String s2sToken = randomUUID().toString();
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(new byte[]{}).withStatus(400)));

        // when
        Throwable throwable = catchThrowable(
            () -> client.updateCase(url(), existingCase(), exceptionRecordRequestData(), s2sToken)
        );

        // then
        assertThat(throwable).isInstanceOf(CaseClientServiceException.class);

        // and
        CaseClientServiceException exception = (CaseClientServiceException) throwable;

        assertThat(exception.getStatus()).isEqualTo(BAD_REQUEST);
        assertThat(exception.getResponse()).contains("No content to map due to end-of-input"); // because byte[]{}
        assertThat(exception.getResponseRawBody()).isEmpty();
    }

    @Test
    public void should_throw_exception_for_unauthorised_service_auth_header() {
        // given
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .willReturn(forbidden().withBody("Calling service is not authorised")));

        // when
        Throwable throwable = catchThrowable(
            () -> client.updateCase(url(), existingCase(), exceptionRecordRequestData(), randomUUID().toString())
        );

        // then
        assertThat(throwable).isInstanceOf(CaseClientServiceException.class);
        CaseClientServiceException response = (CaseClientServiceException) throwable;
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getResponse()).contains("Calling service is not authorised");
    }

    @Test
    public void should_throw_exception_for_invalid_service_auth_header() {
        // given
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .willReturn(unauthorized().withBody("Invalid S2S token")));

        // when
        Throwable throwable = catchThrowable(
            () -> client.updateCase(url(), existingCase(), exceptionRecordRequestData(), randomUUID().toString())
        );

        // then
        assertThat(throwable).isInstanceOf(CaseClientServiceException.class);
        CaseClientServiceException response = (CaseClientServiceException) throwable;
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getResponse()).contains("Invalid S2S token");
    }

    @Test
    public void should_throw_exception_for_server_exception() {
        // given
        stubFor(
            post(urlPathMatching(UPDATE_CASE_URL))
                .willReturn(serverError().withBody("Internal Server error")));

        // when
        Throwable throwable = catchThrowable(
            () -> client.updateCase(url(), existingCase(), exceptionRecordRequestData(), randomUUID().toString())
        );

        // then
        assertThat(throwable).isInstanceOf(CaseClientServiceException.class);
        CaseClientServiceException response = (CaseClientServiceException) throwable;
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getResponse()).contains("Internal Server error");
    }

    private String url() {
        return "http://localhost:" + wiremockOptions.portNumber();
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
            .put("case_details", new JSONObject()
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
