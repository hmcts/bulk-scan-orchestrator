package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHelper {

    public static List<Document> getScannedDocuments(CaseDetails caseDetails) {
        List<Map<String, Map<String, String>>> data = (List<Map<String, Map<String, String>>>) caseDetails.getData().get("scannedDocuments");

        return data.stream().map(TestHelper::createDocumentFromMap).collect(toList());
    }

    private static Document createDocumentFromMap(Map<String, Map<String, String>> object) {
        Map<String, String> doc = object.get("value");
        Document document = new Document(String.valueOf(doc.get("file_name")),
            String.valueOf(doc.get("control_number")),
            String.valueOf(doc.get("type")),
            Instant.now(),
            String.valueOf(doc.get("url"))
        );
        return document;
    }



    public static String s2sSignIn(String s2sName, String s2sSecret, String s2sUrl) {
        Map<String, Object> params = ImmutableMap.of(
            "microservice", s2sName,
            "oneTimePassword", new GoogleAuthenticator().getTotpPassword(s2sSecret)
        );

        Response response = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(s2sUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(params)
            .when()
            .post("/lease")
            .andReturn();

        assertThat(response.getStatusCode()).isEqualTo(200);

        return response
            .getBody()
            .print();
    }
}
