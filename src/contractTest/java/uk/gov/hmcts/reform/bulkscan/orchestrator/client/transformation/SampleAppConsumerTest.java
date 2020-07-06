package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@ExtendWith(PactConsumerTestExt.class)
public class SampleAppConsumerTest {

    private static final String TEST_S2S_TOKEN = "pact-test-s2s-token";

    @Pact(provider = "bulk_scan_sample_app", consumer = "bulk_scan_orchestrator")
    public RequestResponsePact validTransformationPact(PactDslWithProvider builder) throws Exception {
        return builder
            .uponReceiving("Request to transform Bulk Scan ExceptionRecord to a service case")
            .path("/transform-exception-record")
            .method("POST")
            .body(loadJson("transformation/request/valid-exception-record.json"))
            .headers(ImmutableMap.of("ServiceAuthorization", TEST_S2S_TOKEN))
            .willRespondWith()
            .status(OK.value())
            .body(loadJson("transformation/response/success.json"))
            .toPact();
    }

    @Pact(provider = "bulk_scan_sample_app", consumer = "bulk_scan_orchestrator")
    public RequestResponsePact invalidTransformationPact(PactDslWithProvider builder) throws Exception {
        return builder
            .uponReceiving("Request to validate invalid OCR with missing mandatory field 'last_name' for type PERSONAL")
            .path("/transform-exception-record")
            .method("POST")
            .body(loadJson("transformation/request/invalid-exception-record.json"))
            .headers(ImmutableMap.of("ServiceAuthorization", TEST_S2S_TOKEN))
            .willRespondWith()
            .status(UNPROCESSABLE_ENTITY.value())
            .body(loadJson("transformation/response/last-name-required.json"))
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "validTransformationPact")
    public void should_transform_valid_exception_record(MockServer mockServer) throws Exception {
        JsonPath response = RestAssured
            .given()
            .headers(ImmutableMap.of("ServiceAuthorization", TEST_S2S_TOKEN))
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(loadJson("transformation/request/valid-exception-record.json"))
            .when()
            .post(mockServer.getUrl() + "/transform-exception-record")
            .then()
            .statusCode(OK.value())
            .and()
            .extract()
            .body()
            .jsonPath();

        assertAllFields(response);
        assertAmountOfAllFields(response);
        assertThat(response.getList("warnings")).containsExactly("'email' is empty");
    }

    @Test
    @PactTestFor(pactMethod = "invalidTransformationPact")
    public void should_response_failure_when_invalid_exception_record(MockServer mockServer) throws Exception {
        JsonPath response = RestAssured
            .given()
            .headers(ImmutableMap.of("ServiceAuthorization", TEST_S2S_TOKEN))
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .body(loadJson("transformation/request/invalid-exception-record.json"))
            .when()
            .post(mockServer.getUrl() + "/transform-exception-record")
            .then()
            .statusCode(UNPROCESSABLE_ENTITY.value())
            .and()
            .extract()
            .body()
            .jsonPath();

        assertThat(response.getList("errors")).containsExactly("'last_name' is required");
        assertThat(response.getList("warnings")).isEmpty();
    }

    private String loadJson(String path) throws Exception {
        return Resources.toString(Resources.getResource(path), Charsets.UTF_8);
    }

    private void assertAllFields(JsonPath jsonPath) {
        Map<String, String> fieldsForCaseDetails = new ImmutableMap.Builder<String, String>()
            .put("case_creation_details.case_type_id", "Bulk_Scanned")
            .put("case_creation_details.event_id", "createCase")
            .put("case_creation_details.case_data.legacyId", "")
            .put("case_creation_details.case_data.firstName", "FIRST")
            .put("case_creation_details.case_data.lastName", "NAME")
            .put("case_creation_details.case_data.dateOfBirth", "2000-01-01")
            .put("case_creation_details.case_data.contactNumber", "")
            .put("case_creation_details.case_data.email", "")
            .put("case_creation_details.case_data.address.addressLine1", "102 Petty France")
            .put("case_creation_details.case_data.address.addressLine2", "")
            .put("case_creation_details.case_data.address.addressLine3", "")
            .put("case_creation_details.case_data.address.postCode", "SW1H 9AJ")
            .put("case_creation_details.case_data.address.postTown", "")
            .put("case_creation_details.case_data.address.county", "")
            .put("case_creation_details.case_data.address.country", "")
            .put("case_creation_details.case_data.scannedDocuments[0].value.type", "Form")
            .put("case_creation_details.case_data.scannedDocuments[0].value.subtype", "XYZ")
            .put("case_creation_details.case_data.scannedDocuments[0].value.url.document_url", "url")
            .put("case_creation_details.case_data.scannedDocuments[0].value.url.document_binary_url", "binary-url")
            .put(
                "case_creation_details.case_data.scannedDocuments[0].value.url.document_filename",
                "987654321-123456789.pdf"
            )
            .put("case_creation_details.case_data.scannedDocuments[0].value.controlNumber", "987654321")
            .put("case_creation_details.case_data.scannedDocuments[0].value.fileName", "987654321-123456789.pdf")
            .put("case_creation_details.case_data.scannedDocuments[0].value.scannedDate", "2019-08-01T00:01:02.345")
            .put("case_creation_details.case_data.scannedDocuments[0].value.deliveryDate", "2019-08-01T01:02:03.456")
            .put("case_creation_details.case_data.scannedDocuments[0].value.exceptionRecordReference", "id")
            .put("case_creation_details.case_data.bulkScanCaseReference", "id")
            .build();

        fieldsForCaseDetails.forEach((path, value) -> {
            var stringAssert = assertThat(jsonPath.getString(path));

            if (value.isEmpty()) {
                stringAssert.isNull();
            } else {
                stringAssert.isEqualTo(value);
            }
        });
    }

    private void assertAmountOfAllFields(JsonPath jsonPath) {
        Map<String, Integer> fieldsForCaseDetails = new ImmutableMap.Builder<String, Integer>()
            .put("case_creation_details", 3)
            .put("case_creation_details.case_data", 9)
            .put("case_creation_details.case_data.address", 7)
            .put("case_creation_details.case_data.scannedDocuments[0]", 1)
            .put("case_creation_details.case_data.scannedDocuments[0].value", 8)
            .put("case_creation_details.case_data.scannedDocuments[0].value.url", 3)
            .build();

        assertThat(jsonPath.getList("case_creation_details.case_data.scannedDocuments")).hasSize(1);

        fieldsForCaseDetails.forEach((path, size) ->
            assertThat(jsonPath.getMap(path, String.class, String.class)).hasSize(size)
        );
    }
}
