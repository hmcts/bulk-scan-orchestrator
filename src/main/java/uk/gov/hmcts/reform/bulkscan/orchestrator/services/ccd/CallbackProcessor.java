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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachToCaseEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.checkForDuplicatesOrElse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getDocumentNumbers;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.insertNewRecords;

@Service
public class CallbackProcessor {
    private static final Logger log = LoggerFactory.getLogger(CallbackProcessor.class);
    private final CcdApi ccdApi;

    public CallbackProcessor(CcdApi ccdApi) {
        this.ccdApi = ccdApi;
    }

    @SuppressWarnings("squid:S1135")
    //TODO: RPE-822 cleanup
    public List<String> process(String eventType, String eventId, CaseDetails caseDetails) {
        return Validation
            .combine(
                isAttachEvent(eventType),
                isAttachToCaseEvent(eventId),
                hasJurisdiction(caseDetails),
                hasCaseTypeId(caseDetails),
                hasCaseReference(caseDetails),
                hasAScannedRecord(caseDetails),
                hasCaseDetails(caseDetails)
            )
            .ap(this::attachCase)
            .getOrElseGet(Value::toJavaList);
    }

    @SuppressWarnings({"squid:S1172", "squid:S1135", "squid:S1854"})
    //TODO: RPE-822 these are for the validations of the incoming request and is a WIP
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
            createEventSummary(exceptionRecord, theCase),
            event);
    }

    private void throwDuplicateError(String caseRef, Set<String> duplicateIds) {
        throw new CallbackException(
            format("Document with documentIds %s is already attached to %s", duplicateIds, caseRef)
        );
    }

    private String createEventSummary(CaseDetails exceptionRecord, CaseDetails theCase) {
        return format("Attaching exception record(%d) document numbers:%s to case:%d",
            exceptionRecord.getId(),
            getDocumentNumbers(Documents.getScannedRecords(exceptionRecord.getData())),
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
