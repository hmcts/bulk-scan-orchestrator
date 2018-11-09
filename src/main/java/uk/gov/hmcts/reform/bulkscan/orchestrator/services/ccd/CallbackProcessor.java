package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.Value;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasAScannedDocument;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachToCaseEvent;

@Service
public class CallbackProcessor {
    private static final Logger log = LoggerFactory.getLogger(CallbackProcessor.class);
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";
    private static final String SCAN_RECORDS = "scanRecords";
    private static final String DOCUMENT_NUMBER = "documentNumber";

    private final CcdApi ccdApi;

    public CallbackProcessor(CcdApi ccdApi) {
        this.ccdApi = ccdApi;
    }

    @SuppressWarnings("squid:S1135")
    //TODO WIP
    public List<String> process(String eventType, String eventId, CaseDetails caseDetails) {
        return Validation
            .combine(
                isAttachEvent(eventType),
                isAttachToCaseEvent(eventId),
                hasJurisdiction(caseDetails),
                hasCaseTypeId(caseDetails),
                hasCaseReference(caseDetails),
                hasAScannedDocument(caseDetails),
                hasCaseDetails(caseDetails)
            )
            .ap(this::attachCase)
            .getOrElseGet(Value::toJavaList);
    }

    @SuppressWarnings({"squid:S1172", "squid:S1135", "squid:S1854"})
    //TODO these are for the validations of the incoming request and is a WIP
    private List<String> attachCase(String theType,
                                    String anEventId,
                                    String exceptionRecordJurisdiction,
                                    String caseTypeId,
                                    String caseRef,
                                    List<Map<String, Object>> exceptionDocuments,
                                    CaseDetails exceptionRecord) {
        try {
            attachCase(exceptionRecordJurisdiction, caseRef, exceptionRecord, exceptionDocuments);
            return success();
        } catch (CallbackException e) {
            return createErrorList(e);
        }
    }

    //FOR the to do warnings
    @SuppressWarnings("squid:S1135")
    private void attachCase(String exceptionRecordJurisdiction,
                            String caseRef,
                            CaseDetails exceptionRecord,
                            List<Map<String, Object>> exceptionDocuments) {
        CaseDetails theCase = ccdApi.getCase(caseRef, exceptionRecordJurisdiction);

        //TODO check for missing scannedDocs element else empty list ?
        List<Map<String, Object>> existingDocuments = getDocuments(theCase);

        //TODO deal with more than one document in the exception record.
        //This is done so exception record does not change state if there is a document error
        checkForDuplicateAttachment(exceptionDocuments, caseRef, existingDocuments);

        Map<String, Object> data = insertNewScannedDocument(exceptionDocuments, existingDocuments);
        StartEventResponse event = ccdApi.startAttachScannedDocs(theCase);
        ccdApi.attachExceptionRecord(theCase, data, createEventSummary(exceptionRecord, theCase), event);
    }

    private String createEventSummary(CaseDetails exceptionRecord, CaseDetails theCase) {
        return format("Attaching exception record(%d) document number:%s to case:%d",
            exceptionRecord.getId(),
            getDocumentNumber(exceptionRecord.getData()),
            theCase.getId());
    }

    private String getDocumentNumber(Map<String, Object> data) {
        return (String) getScannedDocuments(data).get(0).get(DOCUMENT_NUMBER);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getDocuments(CaseDetails theCase) {
        return (List<Map<String, Object>>) theCase.getData().get(SCANNED_DOCUMENTS);
    }

    @Nonnull
    private List<String> createErrorList(CallbackException e) {
        String message = e.getMessage();
        log.error(message, e);
        return ImmutableList.of(message);
    }

    @Nonnull
    private List<String> success() {
        return emptyList();
    }

    @SuppressWarnings("squid:S1135")
    private Map<String, Object> insertNewScannedDocument(List<Map<String, Object>> exceptionDocuments,
                                                         List<Map<String, Object>> existingDocuments) {
        //TODO assert that there is one document in the exception record.
        existingDocuments.add(exceptionDocuments.get(0));
        return ImmutableMap.of(SCANNED_DOCUMENTS, existingDocuments);
    }

    private void checkForDuplicateAttachment(List<Map<String, Object>> exceptionDocuments,
                                             String caseRef,
                                             List<Map<String, Object>> existingDocuments) {
        String exceptionRecordId = (String) exceptionDocuments.get(0).get(DOCUMENT_NUMBER);
        Set<String> existingDocumentIds = existingDocuments.stream()
            .map(document -> (String) document.get(DOCUMENT_NUMBER))
            .collect(toSet());
        if (existingDocumentIds.contains(exceptionRecordId)) {
            throw new CallbackException(
                format("Document with documentId %s is already attached to %s", exceptionRecordId, caseRef)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getScannedDocuments(Map<String, Object> exceptionData) {
        return (List<Map<String, Object>>) exceptionData.get(SCAN_RECORDS);
    }
}
