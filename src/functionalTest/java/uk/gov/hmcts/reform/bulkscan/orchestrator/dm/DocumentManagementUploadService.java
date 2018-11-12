package uk.gov.hmcts.reform.bulkscan.orchestrator.dm;

import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.document.DocumentUploadClientApi;
import uk.gov.hmcts.reform.document.domain.UploadResponse;
import uk.gov.hmcts.reform.document.utils.InMemoryMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static java.util.Collections.singletonList;

@Service
public class DocumentManagementUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentManagementUploadService.class);

    private final DocumentUploadClientApi documentUploadClientApi;

    private final CcdAuthenticatorFactory ccdAuthenticatorFactory;

    private static final String FILES_NAME = "files";
    public static final String JURSIDICTION = "BULKSCAN";

    DocumentManagementUploadService(
        CcdAuthenticatorFactory ccdAuthenticatorFactory,
        DocumentUploadClientApi documentUploadClientApi
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
        byte[] payload = fileContentAsBytes(filePath);
        return uploadFile(displayName, payload);
    }

    public String uploadFile(String displayName, File file) throws IOException {
        log.info("Uploading {} to DM store", displayName);
        return uploadFile(displayName, Files.readAllBytes(file.toPath()));
    }

    private String uploadFile(String displayName, byte[] payload) {
        MultipartFile file = new InMemoryMultipartFile(
            FILES_NAME,
            displayName,
            MediaType.APPLICATION_PDF_VALUE,
            payload
        );

        CcdAuthenticator authenticator = ccdAuthenticatorFactory.createForJurisdiction(JURSIDICTION);

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


    public static String fileContentAsString(String file) {
        return new String(fileContentAsBytes(file), StandardCharsets.UTF_8);
    }

    public static byte[] fileContentAsBytes(String file) {
        try {
            return Resources.toByteArray(Resources.getResource(file));
        } catch (IOException e) {
            throw new RuntimeException("Could not load file" + file, e);
        }
    }

}
