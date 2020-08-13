package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationResponseTestData.errorResponse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationResponseTestData.invalidDataResponse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationResponseTestData.successResponse;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class TransformationClientTest {

    private static final String TRANSFORMATION_URL = "/transform-exception-record";

    @Autowired
    private TransformationClient client;

    @Value("${service-config.services[0].transformation-url}")
    private String transformationUrl;

    @Test
    public void should_return_case_details_for_successful_transformation() throws Exception {

        // given
        String s2sToken = randomUUID().toString();

        stubFor(
            post(urlPathMatching(TRANSFORMATION_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .withRequestBody(matchingJsonPath("scanned_documents[0].type", matching("[a-z]+")))
                .willReturn(okJson(successResponse().toString()))
        );

        // when
        SuccessfulTransformationResponse response = client.transformCaseData(
            transformationUrl,
            sampleTransformationRequest(),
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
            post(urlPathMatching(TRANSFORMATION_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(errorResponse().toString()).withStatus(UNPROCESSABLE_ENTITY.value())));

        // when
        UnprocessableEntity exception = catchThrowableOfType(
            () -> client.transformCaseData(transformationUrl, sampleTransformationRequest(), s2sToken),
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
            post(urlPathMatching(TRANSFORMATION_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(invalidDataResponse().toString()).withStatus(BAD_REQUEST.value())));

        // when
        BadRequest exception = catchThrowableOfType(
            () -> client.transformCaseData(transformationUrl, sampleTransformationRequest(), s2sToken),
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
            post(urlPathMatching(TRANSFORMATION_URL))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .willReturn(aResponse().withBody(new byte[]{}).withStatus(BAD_REQUEST.value())));

        // when
        BadRequest exception = catchThrowableOfType(
            () -> client.transformCaseData(transformationUrl, sampleTransformationRequest(), s2sToken),
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
            post(urlPathMatching(TRANSFORMATION_URL))
                .willReturn(forbidden().withBody("Calling service is not authorised")));

        // when
        Forbidden exception = catchThrowableOfType(
            () -> client.transformCaseData(
                transformationUrl,
                sampleTransformationRequest(),
                randomUUID().toString()
            ),
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
            post(urlPathMatching(TRANSFORMATION_URL))
                .willReturn(unauthorized().withBody("Invalid S2S token")));

        // when
        Unauthorized exception = catchThrowableOfType(
            () -> client.transformCaseData(
                transformationUrl,
                sampleTransformationRequest(),
                randomUUID().toString()
            ),
            Unauthorized.class
        );

        // then
        assertThat(exception.getResponseBodyAsString()).isEqualTo("Invalid S2S token");
    }

    @Test
    public void should_throw_exception_for_server_exception() {
        // given
        stubFor(
            post(urlPathMatching(TRANSFORMATION_URL))
                .willReturn(serverError().withBody("Internal Server error")));

        // when
        InternalServerError exception = catchThrowableOfType(
            () -> client.transformCaseData(
                transformationUrl,
                sampleTransformationRequest(),
                randomUUID().toString()
            ),
            InternalServerError.class
        );

        // then
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getResponseBodyAsString()).contains("Internal Server error");
    }

    private TransformationRequest sampleTransformationRequest() {
        return new TransformationRequest(
            "id",
            "some_case_type",
            "envelope_id",
            false,
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
}
