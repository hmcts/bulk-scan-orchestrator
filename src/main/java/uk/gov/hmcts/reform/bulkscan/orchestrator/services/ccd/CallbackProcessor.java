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
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
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
                                    CaseDetails exceptionRecord) {
        try {
            attachCase(exceptionRecordJurisdiction, caseRef, exceptionRecord);
            return success();
        } catch (CallbackException e) {
            return createErrorList(e);
        }
    }

    private void attachCase(String exceptionRecordJurisdiction,
                            String caseRef,
                            CaseDetails exceptionRecord) {
        CaseDetails theCase = ccdApi.getCase(caseRef, exceptionRecordJurisdiction);
        StartEventResponse event = ccdApi.startAttachScannedDocs(theCase);
        Map<String, Object> data = insertNewScannedDocument(exceptionRecord.getData(), theCase.getData());
        ccdApi.attachExceptionRecord(theCase, data, createEventSummary(exceptionRecord, theCase), event);
    }

    private String createEventSummary(CaseDetails exceptionRecord, CaseDetails theCase) {
        return format("Attaching exception record(%d) document number:%s to case:%d",
            exceptionRecord.getId(),
            getDocumentNumber(exceptionRecord.getData()),
            theCase.getId());
    }

    private String getDocumentNumber(Map<String, Object> data) {
        return (String) getScannedDocuments(data).get(0).get("documentNumber");
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

    @SuppressWarnings({"unchecked", "squid:S1135"})
    //TODO WIP
    private Map<String, Object> insertNewScannedDocument(Map<String, Object> exceptionData,
                                                         Map<String, Object> caseData) {
        //TODO check SCANNED_DOCUMENTS exists and has a document
        //TODO check that document Id is unique and not duplicate in caseData
        List<Map<String, Object>> caseList = (List<Map<String, Object>>) caseData.get(SCANNED_DOCUMENTS);
        caseList.addAll(getScannedDocuments(exceptionData));
        return ImmutableMap.of(SCANNED_DOCUMENTS, caseList);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getScannedDocuments(Map<String, Object> exceptionData) {
        return (List<Map<String, Object>>) exceptionData.get(SCAN_RECORDS);
    }
}
