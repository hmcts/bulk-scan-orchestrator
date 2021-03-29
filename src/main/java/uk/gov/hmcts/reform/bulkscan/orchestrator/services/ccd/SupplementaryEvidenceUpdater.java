package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.EVIDENCE_HANDLED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;

@Component
public class SupplementaryEvidenceUpdater {
    private static final Logger log = LoggerFactory.getLogger(SupplementaryEvidenceUpdater.class);

    private final CcdApi ccdApi;
    private final AttachScannedDocumentsValidator scannedDocumentsValidator;

    public SupplementaryEvidenceUpdater(
        CcdApi ccdApi,
        AttachScannedDocumentsValidator scannedDocumentsValidator
    ) {
        this.ccdApi = ccdApi;
        this.scannedDocumentsValidator = scannedDocumentsValidator;
    }

    public void updateSupplementaryEvidence(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef
    ) {
        CaseDetails theCase = ccdApi.getCase(targetCaseCcdRef, callBackEvent.exceptionRecordJurisdiction);
        List<Map<String, Object>> targetCaseDocuments = Documents.getScannedDocuments(theCase);

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
            List<Map<String, Object>> newCaseDocuments = attachExceptionRecordReference(
                documentsToAttach,
                callBackEvent.exceptionRecordId
            );

            StartEventResponse ccdStartEvent =
                ccdApi.startAttachScannedDocs(theCase, callBackEvent.idamToken, callBackEvent.userId);

            Map<String, Object> newCaseData = buildCaseData(newCaseDocuments, targetCaseDocuments);

            final String eventSummary = createEventSummary(theCase, callBackEvent.exceptionRecordId, newCaseDocuments);

            log.info(eventSummary);

            ccdApi.attachExceptionRecord(
                theCase,
                callBackEvent.idamToken,
                callBackEvent.userId,
                newCaseData,
                eventSummary,
                ccdStartEvent
            );

            log.info(
                "Attached Exception Record to a case in CCD. ER ID: {}. Case ID: {}",
                callBackEvent.exceptionRecordId,
                theCase.getId()
            );
        }
    }

    private Map<String, Object> buildCaseData(
        List<Map<String, Object>> exceptionDocuments,
        List<Map<String, Object>> existingDocuments
    ) {
        List<Object> documents = Documents.concatDocuments(exceptionDocuments, existingDocuments);
        return ImmutableMap.of(SCANNED_DOCUMENTS, documents, EVIDENCE_HANDLED, YesNoFieldValues.NO);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> attachExceptionRecordReference(
        List<Map<String, Object>> exceptionDocuments,
        Long exceptionRecordReference
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

                return ImmutableMap.<String, Object>of("value", copiedDocumentContent);
            })
            .collect(toList());
    }

    private String createEventSummary(
        CaseDetails theCase,
        Long exceptionRecordId,
        List<Map<String, Object>> exceptionDocuments
    ) {
        return String.format(
            "Attaching exception record(%d) document numbers:%s to case:%d",
            exceptionRecordId,
            Documents.getDocumentNumbers(exceptionDocuments),
            theCase.getId()
        );
    }
}
