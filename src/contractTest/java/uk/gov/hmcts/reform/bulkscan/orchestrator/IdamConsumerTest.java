package uk.gov.hmcts.reform.bulkscan.orchestrator;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
public class IdamConsumerTest {

    private static final String SAMPLE_ACCESS_TOKEN = "111";

    @Pact(provider = "idam_api", consumer = "bulk-scan-orchestrator")
    public RequestResponsePact userDetailsPact(PactDslWithProvider builder) {
        return builder
            .given("User for given access token exists")
            .uponReceiving("Request to get user details")
            .path("/details")
            .method("GET")
            .headers(ImmutableMap.of(HttpHeaders.AUTHORIZATION, SAMPLE_ACCESS_TOKEN))
            .willRespondWith()
            .status(200)
            .body(new PactDslJsonBody()
                .stringValue("id", "123")
                .stringValue("email", "caseofficer@fake.hmcts.net")
                .stringValue("forename", "Case")
                .stringValue("surname", "Officer")
                .stringValue("roles", new PactDslJsonArray().stringValue("caseofficer-role").toString())
            )
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "userDetailsPact")
    public void should_get_user_details(MockServer mockServer) throws Exception {
        JsonPath response = RestAssured
            .given()
            .headers(ImmutableMap.of(HttpHeaders.AUTHORIZATION, SAMPLE_ACCESS_TOKEN))
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get(mockServer.getUrl() + "/details")
            .then()
            .statusCode(200)
            .and()
            .extract()
            .body()
            .jsonPath();

        assertThat(response.getString("id")).isNotBlank();
    }
}
