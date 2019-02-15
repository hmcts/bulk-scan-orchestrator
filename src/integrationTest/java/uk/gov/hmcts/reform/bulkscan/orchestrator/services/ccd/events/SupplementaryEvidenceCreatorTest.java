package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.microsoft.azure.servicebus.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.MessageSender;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_EVENT_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;

@ExtendWith(SpringExtension.class)
@IntegrationTest
@AutoConfigureWireMock(port = 0)
class SupplementaryEvidenceCreatorTest {

    private static final Message MOCK_MESSAGE = new Message(fileContentAsString(
        "servicebus/message/supplementary-evidence-example.json"
    ));
    private static final String MOCK_RESPONSE = fileContentAsString("ccd/response/sample-case.json");

    @Autowired
    @Lazy
    private WireMockServer server;

    @Autowired
    @Lazy
    private MessageSender messageSender;

    @DisplayName("Should call ccd to attach supplementary evidence for caseworker")
    @Test
    void should_call_ccd_to_attach_supplementary_evidence_for_caseworker() {
        // given
        WireMock.configureFor(server.port());
        givenThat(get(GET_CASE_URL).willReturn(aResponse().withBody(MOCK_RESPONSE)));

        // when
        messageSender.send(MOCK_MESSAGE);

        // then
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .until(() -> {
                server.verify(postRequestedFor(urlPathEqualTo(CASE_EVENT_URL)));

                return true;
            });
    }
}
