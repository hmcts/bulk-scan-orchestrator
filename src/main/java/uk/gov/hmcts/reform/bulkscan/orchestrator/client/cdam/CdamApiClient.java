package uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.IdamCachedClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CdamApiClient {

    private final CdamApi cdamApi;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final IdamCachedClient idamCachedClient;

    public CdamApiClient(
        CdamApi cdamApi,
        AuthTokenGenerator s2sTokenGenerator,
        IdamCachedClient idamCachedClient
    ) {
        this.cdamApi = cdamApi;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.idamCachedClient = idamCachedClient;
    }

    public Map<String, String> getDocumentHash(
        String jurisdiction,
        List<Document> documentList
    ) {

        var s2sToken = s2sTokenGenerator.generate();
        var idamCredential = idamCachedClient.getIdamCredentials(jurisdiction);

        Map<String, String> hashTokenMap = new HashMap<String, String>();
        for (Document document : documentList) {
            String docHashToken = cdamApi.getDocumentHash(s2sToken, idamCredential.accessToken, document.uuid);
            hashTokenMap.put(document.uuid, docHashToken);
        }
        return hashTokenMap;
    }

    public String getDocumentHash(
        String jurisdiction,
        Document document
    ) {

        var s2sToken = s2sTokenGenerator.generate();
        var idamCredential = idamCachedClient.getIdamCredentials(jurisdiction);

        return cdamApi.getDocumentHash(s2sToken, idamCredential.accessToken, document.uuid);
    }

}