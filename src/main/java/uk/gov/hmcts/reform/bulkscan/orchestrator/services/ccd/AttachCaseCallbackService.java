package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
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
    private final CcdApi ccdApi;

    public AttachCaseCallbackService(CcdApi ccdApi) {
        this.ccdApi = ccdApi;
    }

    public List<String> process(CaseDetails caseDetails) {
        return Validation
            .combine(
                hasJurisdiction(caseDetails),
                hasCaseReference(caseDetails),
                hasAnId(caseDetails),
                hasAScannedRecord(caseDetails)
            )
            .ap(this::attachCase)
            .getOrElseGet(Value::toJavaList);
    }

    private List<String> attachCase(String exceptionRecordJurisdiction,
                                    String caseRef,
                                    Long exceptionRecordReference,
                                    List<Map<String, Object>> exceptionDocuments) {
        try {
            doAttachCase(exceptionRecordJurisdiction, caseRef, exceptionDocuments, exceptionRecordReference);
            return success();
        } catch (CallbackException e) {
            return createErrorList(e);
        }
    }

    private void doAttachCase(String exceptionRecordJurisdiction,
                              String caseRef,
                              List<Map<String, Object>> exceptionDocuments,
                              Long exceptionRecordReference) {
        CaseDetails theCase = ccdApi.getCase(caseRef, exceptionRecordJurisdiction);
        List<Map<String, Object>> scannedDocuments = getScannedDocuments(theCase);

        //This is done so exception record does not change state if there is a document error
        checkForDuplicatesOrElse(
            exceptionDocuments,
            scannedDocuments,
            ids -> throwDuplicateError(caseRef, ids)
        );

        StartEventResponse event = ccdApi.startAttachScannedDocs(theCase);

        ccdApi.attachExceptionRecord(theCase,
            insertNewRecords(exceptionDocuments, scannedDocuments),
            createEventSummary(theCase, exceptionRecordReference, exceptionDocuments),
            event);
    }

    private void throwDuplicateError(String caseRef, Set<String> duplicateIds) {
        throw new CallbackException(
            format("Document(s) with control number %s are already attached to case reference: %s",
                duplicateIds, caseRef)
        );
    }

    private String createEventSummary(CaseDetails theCase,
                                      Long exceptionRecordId,
                                      List<Map<String, Object>> exceptionDocuments) {
        return format("Attaching exception record(%d) document numbers:%s to case:%d",
            exceptionRecordId,
            getDocumentNumbers(exceptionDocuments),
            theCase.getId());
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
}
