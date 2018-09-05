package uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.exceptions.ReadEnvelopeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.model.Envelope;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


public class GetEnvelopeTest {

    private static final int PORT = 8089;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

    private BulkScanProcessorClient client;

    @Before
    public void setUp() throws Exception {
        this.client = new BulkScanProcessorClient(
            "http://localhost:" + PORT,
            () -> "some_token"
        );
    }

    @Test
    public void should_retrieve_envelope_from_the_service() {
        // given
        String id = UUID.randomUUID().toString();
        String zipFileName = "hello.zip";

        stubFor(
            get(urlEqualTo("/envelopes/" + id))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                            + "\"id\": \"" + id + "\","
                            + "\"zip_file_name\": \"" + zipFileName + "\""
                            + "}"
                        )
                )
        );

        // when
        Envelope envelope = client.getEnvelopeById(id);

        // then
        assertThat(envelope).isNotNull();
        assertThat(envelope.id).isEqualTo(id);
        assertThat(envelope.zipFileName).isEqualTo(zipFileName);

        // and
        verify(
            getRequestedFor(urlEqualTo("/envelopes/" + id))
                .withHeader("ServiceAuthorization", equalTo("some_token"))
        );
    }

    @Test
    public void should_throw_custom_exception_if_server_returns_server_error() {
        // given
        stubFor(get(anyUrl()).willReturn(status(500)));

        // when
        Throwable exception = catchThrowable(() -> client.getEnvelopeById(UUID.randomUUID().toString()));

        // then
        assertThat(exception)
            .isInstanceOf(ReadEnvelopeException.class);
    }

    @Test
    public void should_throw_custom_exception_if_server_returns_client_error() {
        // given
        stubFor(get(anyUrl()).willReturn(status(401)));

        // when
        Throwable exception = catchThrowable(() -> client.getEnvelopeById(UUID.randomUUID().toString()));

        // then
        assertThat(exception)
            .isInstanceOf(ReadEnvelopeException.class);
    }
}
