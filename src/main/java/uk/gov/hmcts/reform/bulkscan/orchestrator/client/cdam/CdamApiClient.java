package uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.IdamCachedClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CdamApiClient {

    private static final Logger log = LoggerFactory.getLogger(CdamApiClient.class);

    private final CdamApi cdamApi;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final IdamCachedClient idamCachedClient;


    @Value("${cdam.api.enabled}")
    private boolean cdamEnabled;

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

        if (!cdamEnabled) {
            log.info("CDAM not enabled returninf empty Map");
            return Map.of();
        }
        var s2sToken = s2sTokenGenerator.generate();
        var idamCredential = idamCachedClient.getIdamCredentials(jurisdiction);

        Map<String, String> hashTokenMap = new HashMap<String, String>();
        for (Document document : documentList) {
            if (document != null) {
                String docHashToken = cdamApi.getDocumentHash(s2sToken, idamCredential.accessToken, document.uuid);
                hashTokenMap.put(document.uuid, docHashToken);
            }
        }
        return hashTokenMap;
    }

    public String getDocumentHash(
        String jurisdiction,
        Document document
    ) {
        if (!cdamEnabled) {
            return null;
        }

        var s2sToken = s2sTokenGenerator.generate();
        var idamCredential = idamCachedClient.getIdamCredentials(jurisdiction);

        return cdamApi.getDocumentHash(s2sToken, idamCredential.accessToken, document.uuid);
    }

    public void setCdamEnabled(boolean cdamEnabled) {
        this.cdamEnabled = cdamEnabled;
    }
}
