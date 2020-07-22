package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeMessageProcessor;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_BULK_SCAN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.EXCEPTION_RECORD_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
class ExceptionRecordCreationTest {

    private static final String CASE_EVENT_TRIGGER_START_URL = Environment.CASE_EVENT_TRIGGER_START_URL
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD);
    private static final String CASE_SUBMIT_URL = Environment.CASE_SUBMIT_URL
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD);

    private static final String ELASTICSEARCH_EMPTY_RESPONSE = "{\"total\": 0,\"cases\": []}";

    @SpyBean
    private IMessageReceiver messageReceiver;

    @Autowired
    private EnvelopeMessageProcessor envelopeMessageProcessor;

    @BeforeEach
    void before() {
        givenThat(get(GET_CASE_URL).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

        givenThat(get(CASE_EVENT_TRIGGER_START_URL).willReturn(aResponse().withBody(
            "{\"case_details\":null,\"event_id\":\"eid\",\"token\":\"etoken\"}"
        )));

        givenThat(post(CASE_SEARCH_URL).willReturn(
            aResponse().withBody(ELASTICSEARCH_EMPTY_RESPONSE)
        ));

        givenThat(post(EXCEPTION_RECORD_SEARCH_URL).willReturn(
            aResponse().withBody(ELASTICSEARCH_EMPTY_RESPONSE)
        ));
    }

    @DisplayName("Should create exception record for supplementary evidence when case record is not found")
    @Test
    void should_create_exception_record_for_supplementary_evidence_when_case_record_is_not_found() throws Exception {
        given(messageReceiver.receive()).willReturn(messageFromFile("supplementary-evidence-example.json"));

        envelopeMessageProcessor.processNextMessage();

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(getRequestedFor(urlPathEqualTo(GET_CASE_URL)));
                WireMock.verify(postRequestedFor(urlPathEqualTo(CASE_SUBMIT_URL)));
                return true;
            });
    }

    @DisplayName("Should create exception record for supplementary evidence when case ref is not provided")
    @Test
    void should_create_exception_record_for_supplementary_evidence_when_case_ref_is_not_provided() throws Exception {
        Message incompleteSupplementaryMessage = new Message(fileContentAsString(
            "servicebus/message/supplementary-evidence-example.json"
        ).replace(CASE_REF, ""));

        given(messageReceiver.receive()).willReturn(incompleteSupplementaryMessage);

        envelopeMessageProcessor.processNextMessage();

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CASE_SUBMIT_URL)));

                return true;
            });
    }

    @DisplayName("Should create exception record for new exception case type")
    @Test
    void should_create_exception_record_for_new_exception_case_type() throws Exception {
        given(messageReceiver.receive()).willReturn(messageFromFile("exception-example.json"));

        envelopeMessageProcessor.processNextMessage();

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CASE_SUBMIT_URL)));

                return true;
            });
    }

    @DisplayName("Should create exception record for new application case type")
    @Test
    void should_create_exception_record_for_new_application_case_type() throws Exception {
        given(messageReceiver.receive()).willReturn(messageFromFile("new-application-example.json"));

        envelopeMessageProcessor.processNextMessage();

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CASE_SUBMIT_URL)));

                return true;
            });
    }

    @DisplayName("Should create exception record for supplementary evidence with ocr case type")
    @Test
    void should_create_exception_record_for_supplementary_evidence_with_ocr() throws Exception {
        given(messageReceiver.receive()).willReturn(messageFromFile("supplementary-evidence-with-ocr-example.json"));

        envelopeMessageProcessor.processNextMessage();

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CASE_SUBMIT_URL)));

                return true;
            });
    }

    private Message messageFromFile(String fileName) {
        return new Message(fileContentAsString("servicebus/message/" + fileName));
    }
}
