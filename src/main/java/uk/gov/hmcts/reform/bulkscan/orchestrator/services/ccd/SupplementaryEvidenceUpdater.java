package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventSummaryCreator.createEventSummary;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.EVIDENCE_HANDLED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.util.Util.getDocumentUuid;

@Component
public class SupplementaryEvidenceUpdater {
    private static final Logger log = LoggerFactory.getLogger(SupplementaryEvidenceUpdater.class);

    private final CcdApi ccdApi;
    private final AttachScannedDocumentsValidator scannedDocumentsValidator;
    private final CdamApiClient cdamApiClient;

    public SupplementaryEvidenceUpdater(
        CcdApi ccdApi,
        AttachScannedDocumentsValidator scannedDocumentsValidator,
        CdamApiClient cdamApiClient
    ) {
        this.ccdApi = ccdApi;
        this.scannedDocumentsValidator = scannedDocumentsValidator;
        this.cdamApiClient = cdamApiClient;
    }

    public boolean updateSupplementaryEvidence(
        AttachToCaseEventData callBackEvent,
        CaseDetails targetCase,
        String targetCaseCcdRef
    ) {
        boolean attached = false;
        List<Map<String, Object>> targetCaseDocuments = Documents.getScannedDocuments(targetCase);

        scannedDocumentsValidator.verifyExceptionRecordAddsNoDuplicates(
            targetCaseDocuments,
            callBackEvent.exceptionRecordDocuments,
            Long.toString(callBackEvent.exceptionRecordId),
            targetCaseCcdRef
        );

        List<Map<String, Object>> documentsToAttach = Documents.removeAlreadyAttachedDocuments(
            callBackEvent.exceptionRecordDocuments,
            targetCaseDocuments,
            Long.toString(callBackEvent.exceptionRecordId)
        );

        if (!documentsToAttach.isEmpty()) {
            List<Map<String, Object>> newCaseDocuments = amendScanDocs(
                documentsToAttach,
                callBackEvent.exceptionRecordId,
                callBackEvent.exceptionRecordJurisdiction
            );

            log.info("Update SupplementaryEvidence newCaseDocuments {}", newCaseDocuments);

            StartEventResponse ccdStartEvent =
                ccdApi.startAttachScannedDocs(targetCase, callBackEvent.idamToken, callBackEvent.userId);

            Map<String, Object> newCaseData = buildCaseData(newCaseDocuments, targetCaseDocuments);

            final String eventSummary = createEventSummary(
                targetCase.getId(),
                callBackEvent.exceptionRecordId,
                Documents.getDocumentNumbers(newCaseDocuments)
            );

            log.info(eventSummary);

            ccdApi.attachExceptionRecord(
                targetCase,
                callBackEvent.idamToken,
                callBackEvent.userId,
                newCaseData,
                eventSummary,
                ccdStartEvent
            );
            attached = true;
            log.info(
                "Attached Exception Record to a case in CCD. ER ID: {}. Case ID: {}",
                callBackEvent.exceptionRecordId,
                targetCase.getId()
            );
        }
        return attached;
    }

    private Map<String, Object> buildCaseData(
        List<Map<String, Object>> exceptionDocuments,
        List<Map<String, Object>> existingDocuments
    ) {
        List<Object> documents = Documents.concatDocuments(exceptionDocuments, existingDocuments);
        return Map.of(SCANNED_DOCUMENTS, documents, EVIDENCE_HANDLED, YesNoFieldValues.NO);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> amendScanDocs(
        List<Map<String, Object>> exceptionDocuments,
        Long exceptionRecordReference,
        String jurisdiction
    ) {
        List<String> exceptionDocumentsDcns = exceptionDocuments
            .stream()
            .map(Documents::getDocumentId)
            .collect(toList());
        log.info(
            "Attaching documents of {} exception record with following DCNs: {}",
            exceptionRecordReference,
            exceptionDocumentsDcns
        );

        return exceptionDocuments
            .stream()
            .map(document -> {
                HashMap<String, Object> copiedDocumentContent =
                    new HashMap<>((Map<String, Object>) document.get("value"));

                copiedDocumentContent.put(
                    "exceptionRecordReference",
                    String.valueOf(exceptionRecordReference)
                );

                Map url = (Map) copiedDocumentContent.get("url");
                String documentUrl = (String) url.get("document_url");
                String documentUuid = getDocumentUuid(documentUrl);
                log.info(
                    "Call back attach supplemantary evidence to case,jurisdiction {}, exception, {},uuid {}",
                    jurisdiction,
                    exceptionRecordReference,
                    documentUuid
                );
                String documentHash = cdamApiClient.getDocumentHash(jurisdiction, documentUuid);
                url.put("document_hash", documentHash);

                return Map.<String, Object>of("value", copiedDocumentContent);
            })
            .collect(toList());
    }
}
