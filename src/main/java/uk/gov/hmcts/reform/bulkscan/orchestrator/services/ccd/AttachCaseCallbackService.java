package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.Value;
import io.vavr.control.Validation;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasAScannedRecord;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasAnId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.checkForDuplicatesOrElse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getDocumentNumbers;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.insertNewRecords;

@Service
public class AttachCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AttachCaseCallbackService.class);
    private static final String ATTACH_CASE_REFERENCE_FIELD_NAME = "attachToCaseReference";

    private final CcdApi ccdApi;

    public AttachCaseCallbackService(CcdApi ccdApi) {
        this.ccdApi = ccdApi;
    }

    /**
     * Attaches exception record to a case.
     *
     * @return List of errors
     */
    public List<String> process(CaseDetails exceptionRecord) {
        return Validation
            .combine(
                hasJurisdiction(exceptionRecord),
                hasCaseReference(exceptionRecord),
                hasAnId(exceptionRecord),
                hasAScannedRecord(exceptionRecord)
            )
            .ap(this::attachToCase)
            .getOrElseGet(Value::toJavaList);
    }

    //The code below need to be rewritten to reuse the EventPublisher class

    private List<String> attachToCase(
        String exceptionRecordJurisdiction,
        String targetCaseRef,
        Long exceptionRecordId,
        List<Map<String, Object>> exceptionRecordDocuments
    ) {
        try {
            doAttachCase(exceptionRecordJurisdiction, targetCaseRef, exceptionRecordDocuments, exceptionRecordId);
            return emptyList();
        } catch (CallbackException e) {
            log.error(e.getMessage(), e);
            return singletonList(e.getMessage());
        }
    }

    private void doAttachCase(
        String exceptionRecordJurisdiction,
        String targetCaseRef,
        List<Map<String, Object>> exceptionRecordDocuments,
        Long exceptionRecordId
    ) {
        CaseDetails theCase = ccdApi.getCase(targetCaseRef, exceptionRecordJurisdiction);
        List<Map<String, Object>> targetCaseDocuments = getScannedDocuments(theCase);

        verifyExceptionRecordIsNotAttachedToCase(exceptionRecordJurisdiction, exceptionRecordId);

        //This is done so exception record does not change state if there is a document error
        checkForDuplicatesOrElse(
            exceptionRecordDocuments,
            targetCaseDocuments,
            ids -> throwDuplicateError(targetCaseRef, ids)
        );

        attachExceptionRecordReference(exceptionRecordDocuments, exceptionRecordId);

        StartEventResponse event = ccdApi.startAttachScannedDocs(theCase);

        ccdApi.attachExceptionRecord(
            theCase,
            insertNewRecords(exceptionRecordDocuments, targetCaseDocuments),
            createEventSummary(theCase, exceptionRecordId, exceptionRecordDocuments),
            event
        );
    }

    private void verifyExceptionRecordIsNotAttachedToCase(
        String exceptionRecordJurisdiction,
        Long exceptionRecordReference
    ) {
        CaseDetails fetchedExceptionRecord = ccdApi.getCase(
            exceptionRecordReference.toString(),
            exceptionRecordJurisdiction
        );

        if (isExceptionRecordAttachedToCase(fetchedExceptionRecord)) {
            throw new CallbackException("Exception record is already attached to a case");
        }
    }

    @SuppressWarnings("unchecked")
    private void attachExceptionRecordReference(
        List<Map<String, Object>> exceptionDocuments,
        Long exceptionRecordReference
    ) {
        exceptionDocuments.stream().map(doc -> {
            Map<String, Object> document = (Map<String, Object>) doc.get("value");
            document.put("exceptionRecordReference", String.valueOf(exceptionRecordReference));
            return doc;
        }).collect(toList());
    }

    private void throwDuplicateError(String caseRef, Set<String> duplicateIds) {
        throw new CallbackException(
            String.format(
                "Document(s) with control number %s are already attached to case reference: %s",
                duplicateIds,
                caseRef
            )
        );
    }

    private String createEventSummary(
        CaseDetails theCase,
        Long exceptionRecordId,
        List<Map<String, Object>> exceptionDocuments
    ) {
        return String.format(
            "Attaching exception record(%d) document numbers:%s to case:%d",
            exceptionRecordId,
            getDocumentNumbers(exceptionDocuments),
            theCase.getId()
        );
    }

    private boolean isExceptionRecordAttachedToCase(CaseDetails exceptionRecordDetails) {
        Object attachToCaseReference = exceptionRecordDetails
            .getData()
            .get(ATTACH_CASE_REFERENCE_FIELD_NAME);

        return attachToCaseReference != null && Strings.isNotEmpty(attachToCaseReference.toString());
    }
}
