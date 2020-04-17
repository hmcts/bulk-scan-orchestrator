package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.CcdCallbackController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.io.IOException;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.emptyList;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
public class ReclassifyCallbackTest {

    private static final String JOURNEY_CLASSIFICATION_FIELD_NAME = "journeyClassification";

    @Value("${test-url}")
    private String testUrl;

    @Autowired
    private CcdAuthenticatorFactory ccdAuthenticatorFactory;

    @Test
    void should_update_classification_when_request_is_valid() {
        // given
        CallbackRequest callbackRequest = prepareValidCallbackRequest();

        // when
        Response response = callReclassifyEndpoint(callbackRequest);

        // then
        assertThat(response.statusCode()).isEqualTo(200);

        JsonPath responseJson = response.jsonPath();
        assertThat(responseJson.getList("errors")).isEmpty();
        assertThat(responseJson.getList("warnings")).isEmpty();

        Map<String, Object> fieldsInRequest = callbackRequest.getCaseDetails().getData();


        Map<String, Object> fieldsInResponse = response.jsonPath().getMap("data");
        assertThat(fieldsInResponse).isEqualTo(
            getExpectedFieldsInResponse((fieldsInRequest))
        );
    }


    @DisplayName("Each configured jurisdiction should have valid credentials")
    @Test
    void each_jurisdiction_should_have_valid_credentials() throws IOException {
        byte[] response = RestAssured
                .given()
                .relaxedHTTPSValidation()
                .baseUri(testUrl)
                .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator smoke test")
                .get("/idam-config-status")
                .andReturn()
                .asByteArray();
        ObjectMapper mapper = new ObjectMapper();

        mapper.readTree(response).elements().forEachRemaining(responseStatus -> {
            String jurisdiction = responseStatus.get("jurisdiction").asText();
            boolean isCorrect = responseStatus.get("is_correct").asBoolean();
            String errorDescription = responseStatus.get("error_description").asText();

            assertThat(isCorrect)
                    .withFailMessage(
                            "Misconfigured %s jurisdiction,"
                                    + " error description: %s. Check the logs for more details",
                            jurisdiction,
                            errorDescription
                    )
                    .isTrue();
        });
    }

    private Response callReclassifyEndpoint(CallbackRequest callbackRequest) {
        CcdAuthenticator ccdAuthenticator = ccdAuthenticatorFactory.createForJurisdiction("BULKSCAN");

        // when
        return RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .header(AUTHORIZATION, ccdAuthenticator.getUserToken())
            .header(CcdCallbackController.USER_ID, ccdAuthenticator.getUserDetails().getId())
            .body(callbackRequest)
            .when()
            .post("/callback/reclassify-exception-record?ignore-warning=true")
            .andReturn();
    }

    private Map<String, Object> getExpectedFieldsInResponse(Map<String, Object> fieldsInRequest) {
        Map<String, Object> expectedFieldsInResponse = newHashMap(fieldsInRequest);
        expectedFieldsInResponse.put(JOURNEY_CLASSIFICATION_FIELD_NAME, "SUPPLEMENTARY_EVIDENCE_WITH_OCR");
        return expectedFieldsInResponse;
    }

    private CallbackRequest prepareValidCallbackRequest() {
        Map<String, Object> originalFields = ImmutableMap.<String, Object>builder()
            // journeyClassification is the only field that matters to the endpoint
            .put(JOURNEY_CLASSIFICATION_FIELD_NAME, "NEW_APPLICATION")
            .put("poBox", "12345")
            .put("poBoxJurisdiction", "BULKSCAN")
            .put("formType", "bsp-form-1")
            .put("deliveryDate", "2020-04-01T12:34:56.00Z")
            .put("scanOCRData", emptyList())
            .build();

        CaseDetails exceptionRecord = CaseDetails
            .builder()
            .state("ScannedRecordReceived")
            .data(originalFields)
            .build();

        return CallbackRequest
            .builder()
            .eventId("reclassifyRecord")
            .caseDetails(exceptionRecord)
            .build();
    }
}
