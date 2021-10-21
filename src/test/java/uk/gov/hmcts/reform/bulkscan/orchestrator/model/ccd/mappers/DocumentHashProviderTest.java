package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DocumentHashProviderTest {
    private static final String JURISDICTION = "BULKSCAN";

    @Mock
    private CdamApiClient cdamApiClient;

    private DocumentHashProvider documentHashProvider;

    @BeforeEach
    void setUp() {
        documentHashProvider = new DocumentHashProvider(cdamApiClient);
    }

    @Test
    void should_return_hashes() {
        // given
        Document d1 = new Document(
                "filename1.pdf",
                "1111001",
                "Other",
                null,
                Instant.parse("2018-12-01T12:34:56.123Z"),
                "863c495e-d05b-4376-9951-ea489360db6f",
                Instant.parse("2018-12-02T12:30:56.123Z")
        );
        Document d2 = new Document(
                "filename2.pdf",
                "1111002",
                "Other",
                null,
                Instant.parse("2018-12-01T12:34:56.123Z"),
                "863c495e-d05b-5376-9951-ea489360db6f",
                Instant.parse("2018-12-02T12:30:56.123Z")
        );

        given(cdamApiClient.getDocumentHash(JURISDICTION, d1)).willReturn("hash1");
        given(cdamApiClient.getDocumentHash(JURISDICTION, d2)).willReturn("hash2");

        // when
        List<DocumentHashProvider.DocumentAndHash> res =
                documentHashProvider.getDocumentHashes(asList(d1, d2), JURISDICTION);

        // then
        assertThat(res)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(
                        new DocumentHashProvider.DocumentAndHash(d1, "hash1"),
                        new DocumentHashProvider.DocumentAndHash(d2, "hash2")
                );
    }

    @Test
    void should_handle_empty_list() {
        // given

        // when
        List<DocumentHashProvider.DocumentAndHash> res =
                documentHashProvider.getDocumentHashes(emptyList(), JURISDICTION);

        // then
        assertThat(res).isEmpty();
    }
}