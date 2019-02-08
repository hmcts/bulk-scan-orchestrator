package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.microsoft.azure.servicebus.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.MessageSender;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_BULK_SCAN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;

@ExtendWith(SpringExtension.class)
@IntegrationTest
class ExceptionRecordCreatorTest {

    private static final String caseEventTriggerStartUrl = Environment.CASE_EVENT_TRIGGER_START_URL
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD);
    private static final String caseSubmitUrl = Environment.CASE_SUBMIT_URL
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD);

    @Autowired
    @Lazy
    private WireMockServer server;

    @Autowired
    @Lazy
    private MessageSender messageSender;

    @BeforeEach
    void before() {
        WireMock.configureFor(server.port());

        givenThat(get(GET_CASE_URL).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));
        givenThat(get(caseEventTriggerStartUrl).willReturn(aResponse().withBody(
            "{\"case_details\":null,\"event_id\":\"eid\",\"token\":\"etoken\"}"
        )));
    }

    @DisplayName("Should create exception record for supplementary evidence when case record is not found")
    @Test
    void should_create_exception_record_for_supplementary_evidence_when_case_record_is_not_found() {
        messageSender.send(messageFromFile("supplementary-evidence-example.json"));

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                server.verify(getRequestedFor(urlPathEqualTo(GET_CASE_URL)));
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)));
                return true;
            });
    }

    @DisplayName("Should create exception record for supplementary evidence when case ref is not provided")
    @Test
    void should_create_exception_record_for_supplementary_evidence_when_case_ref_is_not_provided() {
        Message incompleteSupplementaryMessage = new Message(fileContentAsString(
            "servicebus/message/supplementary-evidence-example.json"
        ).replace(CASE_REF, ""));

        messageSender.send(incompleteSupplementaryMessage);

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)));

                return true;
            });
    }

    @DisplayName("Should create exception record for new exception case type")
    @Test
    void should_create_exception_record_for_new_exception_case_type() {
        messageSender.send(messageFromFile("exception-example.json"));

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)));

                return true;
            });
    }

    @DisplayName("Should create exception record for new application case type")
    @Test
    void should_create_exception_record_for_new_application_case_type() {
        messageSender.send(messageFromFile("new-application-example.json"));

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)));

                return true;
            });
    }

    private Message messageFromFile(String fileName) {
        return new Message(fileContentAsString("servicebus/message/" + fileName));
    }
}
