package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeEventProcessor;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SUBMIT_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_BULK_SCAN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.EXCEPTION_RECORD_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
class SupplementaryEvidenceCreatorTest {

    private static final Message MOCK_MESSAGE = new Message(fileContentAsString(
        "servicebus/message/supplementary-evidence-example.json"
    ));
    private static final String MOCK_RESPONSE = fileContentAsString("ccd/response/sample-case.json");

    @SpyBean
    private IMessageReceiver messageReceiver;

    @Autowired
    private EnvelopeEventProcessor envelopeEventProcessor;

    private static final String CREATE_EXCEPTION_RECORD_SUBMIT_URL = Environment.CASE_SUBMIT_URL
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD);

    private static final String CREATE_EXCEPTION_START_EVENT_URL = Environment.CASE_EVENT_TRIGGER_START_URL
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD);

    private static final String SERVICE_AUTHORIZATION_HEADER = "ServiceAuthorization";

    // see WireMock mapping json files
    private static final String MOCKED_IDAM_TOKEN_SIG = "q6hDG0Z1Qbinwtl8TgeDrAVV0LlCTRtbQqBYoMjd03k";
    private static final String MOCKED_S2S_TOKEN_SIG =
        "X1-LdZAd5YgGFP16-dQrpqEICqRmcu1zL_zeCLyUqMjb5DVx7xoU-r8yXHfgd4tmmjGqbsBz_kLqgu8yruSbtg";

    private static final String ELASTICSEARCH_EMPTY_RESPONSE = "{\"total\": 0,\"cases\": []}";

    @BeforeEach
    void before() throws Exception {
        WireMock.reset();
        givenThat(get(GET_CASE_URL).willReturn(aResponse().withBody(MOCK_RESPONSE)));
        given(messageReceiver.receive()).willReturn(MOCK_MESSAGE).willReturn(null);
    }

    @DisplayName("Should call ccd to attach supplementary evidence for caseworker")
    @Test
    void should_call_ccd_to_attach_supplementary_evidence_for_caseworker() throws Exception {
        // when
        envelopeEventProcessor.processNextMessage();

        // then
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(Environment.CASE_EVENT_URL)));

                return true;
            });
    }

    @DisplayName("Should create Exception record when attachScannedDocs ccd event is not configured for case type")
    @Test
    void should_create_exception_record_when_attachScannedDocs_start_event_fails() throws Exception {
        // given
        givenThat(attachScannedDocsCcdStartEvent().willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));
        stubCreateExceptionCcdEvents();

        // when
        envelopeEventProcessor.processNextMessage();

        // then
        await()
            .atMost(60, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CREATE_EXCEPTION_RECORD_SUBMIT_URL)));
                return true;
            });
    }

    @DisplayName("Should create Exception record when attachScannedDocs ccd event authorisation is missing")
    @Test
    void should_create_exception_record_when_attachScannedDocs_submit_event_fails() throws Exception {
        // given
        givenThat(attachScannedDocsCcdSubmitEvent().willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));
        stubCreateExceptionCcdEvents();

        // when
        envelopeEventProcessor.processNextMessage();

        // then
        await()
            .atMost(60, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CREATE_EXCEPTION_RECORD_SUBMIT_URL)));
                return true;
            });
    }

    private void stubCreateExceptionCcdEvents() {
        givenThat(exceptionRecordCcdStartEvent().willReturn(aResponse().withBody(
            "{\"case_details\":null,\"event_id\":\"eid\",\"token\":\"etoken\"}"
        )));

        givenThat(post(CASE_SEARCH_URL).willReturn(
            aResponse().withBody(ELASTICSEARCH_EMPTY_RESPONSE)
        ));

        givenThat(post(EXCEPTION_RECORD_SEARCH_URL).willReturn(
            aResponse().withBody(ELASTICSEARCH_EMPTY_RESPONSE)
        ));
    }

    private MappingBuilder attachScannedDocsCcdStartEvent() {
        return get(Environment.ATTACH_DOCS_START_EVENT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG));
    }

    private MappingBuilder attachScannedDocsCcdSubmitEvent() {
        return post(CASE_SUBMIT_URL + "/" + CASE_REF + "/events?ignore-warning=true")
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG));
    }

    private MappingBuilder exceptionRecordCcdStartEvent() {
        return get(CREATE_EXCEPTION_START_EVENT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG));
    }
}
