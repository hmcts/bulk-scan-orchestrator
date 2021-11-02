package uk.gov.hmcts.reform.bulkscan.orchestrator.dm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.CachedIdamCredential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.IdamCachedClient;
import uk.gov.hmcts.reform.ccd.document.am.feign.CaseDocumentClientApi;
import uk.gov.hmcts.reform.ccd.document.am.model.Classification;
import uk.gov.hmcts.reform.ccd.document.am.model.DocumentUploadRequest;
import uk.gov.hmcts.reform.ccd.document.am.model.UploadResponse;
import uk.gov.hmcts.reform.ccd.document.am.util.InMemoryMultipartFile;

import static java.util.Collections.singletonList;

@Service
public class DocumentManagementUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentManagementUploadService.class);

    // needs `case_document_am.url` env var
    private final CaseDocumentClientApi documentUploadClientApi;

    private final AuthTokenGenerator s2sTokenGenerator;
    private final IdamCachedClient idamCachedClient;

    private static final String FILES_NAME = "files";

    DocumentManagementUploadService(
        @Qualifier("processor-s2s-auth") AuthTokenGenerator s2sTokenGenerator,
        CaseDocumentClientApi documentUploadClientApi,
        IdamCachedClient idamCachedClient
    ) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.documentUploadClientApi = documentUploadClientApi;
        this.idamCachedClient = idamCachedClient;

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

        CachedIdamCredential idamCredentials = idamCachedClient.getIdamCredentials(SampleData.JURSIDICTION);
        String s2sToken = s2sTokenGenerator.generate();

        DocumentUploadRequest uploadRequest = new DocumentUploadRequest(
            Classification.RESTRICTED.toString(),
            "Bulkscan_ExceptionRecord",
            "BULKSCAN",
            singletonList(file)
        );
        UploadResponse uploadResponse = documentUploadClientApi.uploadDocuments(
            idamCredentials.accessToken,
            s2sToken,
            uploadRequest
        );


        log.info("{} uploaded to DM store", displayName);

        return uploadResponse
            .getDocuments()
            .get(0)
            .links
            .self
            .href;
    }
}
