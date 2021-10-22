package uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.CachedIdamCredential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.IdamCachedClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CdamApiClientTest {

    @Mock
    private CdamApi cdamApi;
    @Mock
    private AuthTokenGenerator s2sTokenGenerator;
    @Mock
    private IdamCachedClient idamCachedClient;

    @InjectMocks
    private CdamApiClient cdamApiClient;

    private static final String S2S_TOKEN = "s2sToken-123123";
    private static final String IDAM_TOKEN = "idamToken-Dfd322";
    private static final String JURISDICTION = "JUR_TEST";

    @Test
    void should_get_allHashTokens_for_all_docs() {

        var cachedIdamCredential = new CachedIdamCredential(IDAM_TOKEN, "user-1", 132131);
        given(s2sTokenGenerator.generate()).willReturn(S2S_TOKEN);
        given(idamCachedClient.getIdamCredentials(JURISDICTION)).willReturn(cachedIdamCredential);

        var document1Uuid = UUID.randomUUID().toString();
        var document2Uuid = UUID.randomUUID().toString();
        var docHash1 = "23fdasaf3123sdvvs21wdeqa";
        var docHash2 = "asdadad2323232223432cddc";
        given(cdamApi.getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document1Uuid)).willReturn(docHash1);
        given(cdamApi.getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document2Uuid)).willReturn(docHash2);

        cdamApiClient.setCdamEnabled(true);

        Map<String, String> resultMap = cdamApiClient.getDocumentHash(
            JURISDICTION,
            List.of(getDocument(document1Uuid), getDocument(document2Uuid))
        );

        assertThat(resultMap).contains(entry(document1Uuid, docHash1), entry(document2Uuid, docHash2));

        verify(s2sTokenGenerator).generate();
        verify(idamCachedClient).getIdamCredentials(JURISDICTION);
        verify(cdamApi).getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document1Uuid);
        verify(cdamApi).getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document2Uuid);
    }

    @Test
    void should_throw_exception_when_get_HashToken_fails() {

        var cachedIdamCredential = new CachedIdamCredential(IDAM_TOKEN, "user-1", 132131);
        given(s2sTokenGenerator.generate()).willReturn(S2S_TOKEN);
        given(idamCachedClient.getIdamCredentials(JURISDICTION)).willReturn(cachedIdamCredential);

        var document1Uuid = UUID.randomUUID().toString();
        var document2Uuid = UUID.randomUUID().toString();
        var docHash1 = "23fdasaf3123sdvvs21wdeqa";
        given(cdamApi.getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document1Uuid)).willReturn(docHash1);
        given(cdamApi.getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document2Uuid))
            .willThrow(new HttpClientErrorException(HttpStatus.MULTI_STATUS));

        assertThatCode(() -> cdamApiClient.getDocumentHash(
            JURISDICTION,
            List.of(getDocument(document1Uuid), getDocument(document2Uuid))
        ))
            .isInstanceOf(HttpClientErrorException.class)
            .hasMessage("207 MULTI_STATUS");


        verify(s2sTokenGenerator).generate();
        verify(idamCachedClient).getIdamCredentials(JURISDICTION);
        verify(cdamApi).getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document1Uuid);
        verify(cdamApi).getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document2Uuid);
    }

    @Test
    void should_get_hashToken_for_a_doc() {

        var cachedIdamCredential = new CachedIdamCredential(IDAM_TOKEN, "user-1", 132131);
        given(s2sTokenGenerator.generate()).willReturn(S2S_TOKEN);
        given(idamCachedClient.getIdamCredentials(JURISDICTION)).willReturn(cachedIdamCredential);

        var document1Uuid = UUID.randomUUID().toString();
        var docHash1 = "23fdasaf3123sdvvs21wdeqa";
        given(cdamApi.getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document1Uuid)).willReturn(docHash1);

        cdamApiClient.setCdamEnabled(true);

        String result = cdamApiClient.getDocumentHash(
            JURISDICTION,
            getDocument(document1Uuid)
        );

        assertThat(result).isEqualTo(docHash1);

        verify(s2sTokenGenerator).generate();
        verify(idamCachedClient).getIdamCredentials(JURISDICTION);
        verify(cdamApi).getDocumentHash(S2S_TOKEN, IDAM_TOKEN, document1Uuid);
    }

    @Test
    void should_throw_exception_when_get_hashToken_get_errors() {
        given(s2sTokenGenerator.generate()).willReturn(S2S_TOKEN);
        var cachedIdamCredential = new CachedIdamCredential(IDAM_TOKEN, "user-1", 132131);
        given(idamCachedClient.getIdamCredentials(JURISDICTION)).willReturn(cachedIdamCredential);

        var document1Uuid = UUID.randomUUID().toString();
        given(cdamApi.getDocumentHash(anyString(), anyString(), anyString()))
            .willThrow(new HttpClientErrorException(HttpStatus.NOT_IMPLEMENTED));

        assertThatCode(() -> cdamApiClient.getDocumentHash(
            JURISDICTION,
            getDocument(document1Uuid)
        ))
            .isInstanceOf(HttpClientErrorException.class)
            .hasMessage("501 NOT_IMPLEMENTED");
    }


    private static Document getDocument(String documentUuid) {
        return new Document(
            "certificate1.pdf",
            "154565768",
            "other",
            null,
            Instant.now(),
            documentUuid,
            Instant.now()
        );
    }
}
