package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeEventProcessor;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_EVENT_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;

@IntegrationTest
@Disabled
class SupplementaryEvidenceCreatorTest {

    private static final Message MOCK_MESSAGE = new Message(fileContentAsString(
        "servicebus/message/supplementary-evidence-example.json"
    ));
    private static final String MOCK_RESPONSE = fileContentAsString("ccd/response/sample-case.json");

    @SpyBean
    private IMessageReceiver messageReceiver;

    @Autowired
    private EnvelopeEventProcessor envelopeEventProcessor;


    @DisplayName("Should call ccd to attach supplementary evidence for caseworker")
    @Test
    void should_call_ccd_to_attach_supplementary_evidence_for_caseworker() throws Exception {
        // given
        givenThat(get(GET_CASE_URL).willReturn(aResponse().withBody(MOCK_RESPONSE)));
        given(messageReceiver.receive()).willReturn(MOCK_MESSAGE).willReturn(null);

        // when
        envelopeEventProcessor.processNextMessage();

        // then
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                WireMock.verify(postRequestedFor(urlPathEqualTo(CASE_EVENT_URL)));

                return true;
            });
    }
}
