package uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;


@Component
public class CdamApi {

    private static final Logger log = LoggerFactory.getLogger(CdamApi.class);

    private final String documentManagementUrl;
    private final RestTemplate restTemplate;
    private final static String GET_HASH_REQUEST_PATH = "/cases/documents/{documentId}/token";

    public CdamApi(
        @Value("${document_management.url}") final String documentManagementUrl,
        RestTemplate restTemplate
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.restTemplate = restTemplate;
    }

    public String getDocumentHash(
        String s2sToken,
        String idamToken,
        String documentUUID
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sToken);
        headers.add("Authorization", idamToken);
        headers.add("Accept", APPLICATION_JSON.toString());

        String url =
            UriComponentsBuilder
                .fromHttpUrl(documentManagementUrl + GET_HASH_REQUEST_PATH)
                .buildAndExpand(documentUUID.toString())
                .toUri()
                .toString();

        log.info("Get hashtoken for {}", url);

        return restTemplate
            .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GetDocumentHashResponse.class)
            .getBody().hashToken;
    }

}
