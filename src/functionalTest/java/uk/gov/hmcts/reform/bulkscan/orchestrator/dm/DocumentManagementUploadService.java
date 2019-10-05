package uk.gov.hmcts.reform.bulkscan.orchestrator.dm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.document.DocumentUploadClientApi;
import uk.gov.hmcts.reform.document.domain.UploadResponse;
import uk.gov.hmcts.reform.document.utils.InMemoryMultipartFile;

import static java.util.Collections.singletonList;

@Service
public class DocumentManagementUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentManagementUploadService.class);

    private final DocumentUploadClientApi documentUploadClientApi;

    private final CcdAuthenticatorFactory ccdAuthenticatorFactory;

    private static final String FILES_NAME = "files";

    DocumentManagementUploadService(
        @Value("${document_management.url}") final String dmUri,
        ObjectMapper objectMapper,
        CcdAuthenticatorFactory ccdAuthenticatorFactory
    ) {
        this.ccdAuthenticatorFactory = ccdAuthenticatorFactory;

        //ObjectMapper objectMapper = new ObjectMapper();
        //objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        //objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(false));

        this.documentUploadClientApi =
            new DocumentUploadClientApi(
                dmUri,
                new RestTemplate(clientHttpRequestFactory()),
                objectMapper
            );
    }

    /**
     * Uploads file to dm service.
     *
     * @return URL to uploaded file
     */
    public String uploadToDmStore(String displayName, String filePath) {
        log.info("Uploading {} to DM store", displayName);
        MultipartFile file = new InMemoryMultipartFile(
            FILES_NAME,
            displayName,
            MediaType.APPLICATION_PDF_VALUE,
            SampleData.fileContentAsBytes(filePath)
        );

        CcdAuthenticator authenticator = ccdAuthenticatorFactory.createForJurisdiction(SampleData.JURSIDICTION);

        UploadResponse uploadResponse = documentUploadClientApi.upload(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            singletonList(file)
        );
        log.info("{} uploaded to DM store", displayName);

        return uploadResponse
            .getEmbedded()
            .getDocuments()
            .get(0)
            .links
            .self
            .href;
    }

    public HttpComponentsClientHttpRequestFactory clientHttpRequestFactory() {
        return new HttpComponentsClientHttpRequestFactory(getHttpClient());
    }

    private CloseableHttpClient getHttpClient() {
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(30000)
            .setConnectionRequestTimeout(30000)
            .setSocketTimeout(60000)
            .build();

        return HttpClientBuilder
            .create()
            .useSystemProperties()
            .setDefaultRequestConfig(config)
            .build();
    }
}
