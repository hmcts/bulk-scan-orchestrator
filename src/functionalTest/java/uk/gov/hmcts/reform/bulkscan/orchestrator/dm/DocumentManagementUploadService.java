package uk.gov.hmcts.reform.bulkscan.orchestrator.dm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
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

    private final CcdAuthenticatorFactory ccdAuthenticatorFactory;

    private static final String FILES_NAME = "files";

    DocumentManagementUploadService(
        CcdAuthenticatorFactory ccdAuthenticatorFactory,
        CaseDocumentClientApi documentUploadClientApi
    ) {
        this.ccdAuthenticatorFactory = ccdAuthenticatorFactory;
        this.documentUploadClientApi = documentUploadClientApi;
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

        DocumentUploadRequest uploadRequest = new DocumentUploadRequest(
            Classification.RESTRICTED.toString(),
            "Bulkscan_ExceptionRecord",
            "BULKSCAN",
            singletonList(file)
        );
        UploadResponse uploadResponse = documentUploadClientApi.uploadDocuments(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
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
