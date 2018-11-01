package uk.gov.hmcts.reform.bulkscan.orchestrator.dm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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
        CcdAuthenticatorFactory ccdAuthenticatorFactory,
        DocumentUploadClientApi documentUploadClientApi
    ) {
        this.ccdAuthenticatorFactory = ccdAuthenticatorFactory;
        this.documentUploadClientApi = documentUploadClientApi;
    }

    public UploadResponse uploadToDmStore(String displayName, String filePath) {
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
        return uploadResponse;
    }
}
