package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeMessageProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

    @Mock
    private ServiceBusReceivedMessageContext messageContext;

    @Mock
    private ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);

    @MockBean
    private CdamApiClient cdamApiClient;

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

        given(cdamApiClient.getDocumentHash(anyString(), any(Document.class))).willReturn("hash");
    }

    @DisplayName("Should create exception record for supplementary evidence when case record is not found")
    @Test
    void should_create_exception_record_for_supplementary_evidence_when_case_record_is_not_found() {
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody())
            .willReturn(
                BinaryData.fromString(fileContentAsString("servicebus/message/supplementary-evidence-example.json"))
            );

        envelopeMessageProcessor.processMessage(messageContext);

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
    void should_create_exception_record_for_supplementary_evidence_when_case_ref_is_not_provided() {
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromString(fileContentAsString(
            "servicebus/message/supplementary-evidence-example.json"
        ).replace(CASE_REF, "")));

        envelopeMessageProcessor.processMessage(messageContext);

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
    void should_create_exception_record_for_new_exception_case_type() {
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromString(fileContentAsString(
            "servicebus/message/exception-example.json"
        )));

        envelopeMessageProcessor.processMessage(messageContext);

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
    void should_create_exception_record_for_new_application_case_type() {
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromString(fileContentAsString(
            "servicebus/message/new-application-example.json"
        )));

        envelopeMessageProcessor.processMessage(messageContext);

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
    void should_create_exception_record_for_supplementary_evidence_with_ocr() {
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromString(fileContentAsString(
            "servicebus/message/supplementary-evidence-with-ocr-example.json"
        )));

        envelopeMessageProcessor.processMessage(messageContext);

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CASE_SUBMIT_URL)));

                return true;
            });
    }

}
