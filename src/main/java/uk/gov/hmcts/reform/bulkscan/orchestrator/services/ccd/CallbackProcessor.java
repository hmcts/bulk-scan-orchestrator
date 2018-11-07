package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.Value;
import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
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

    private CcdAuthenticatorFactory authFactory;
    private final CallbackCcdApi ccdApi;

    public CallbackProcessor(CallbackCcdApi ccdApi, CcdAuthenticatorFactory authFactory) {
        this.authFactory = authFactory;
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
            attachCase(exceptionRecordJurisdiction, caseRef, exceptionDocuments);
            return success();
        } catch (CallbackException e) {
            return createErrorList(e);
        }
    }

    private void attachCase(String exceptionRecordJurisdiction,
                            String caseRef,
                            List<Map<String, Object>> exceptionDocuments) {
        CcdAuthenticator authenticator = authFactory.createForJurisdiction(exceptionRecordJurisdiction);
        CaseDetails theCase = ccdApi.getCase(caseRef, authenticator);
        StartEventResponse event = ccdApi.startAttachScannedDocs(caseRef, authenticator, theCase);
        ccdApi.attachExceptionRecord(caseRef,
            authenticator,
            theCase,
            insertNewScannedDocument(exceptionDocuments, theCase.getData()),
            event.getEventId(),
            event.getToken()
        );
    }

    @NotNull
    private List<String> createErrorList(CallbackException e) {
        String message = e.getMessage();
        log.error(message, e);
        return ImmutableList.of(message);
    }

    private List<String> success() {
        return emptyList();
    }

    @SuppressWarnings({"unchecked", "squid:S1135"})
    //TODO WIP
    private Map<String, Object> insertNewScannedDocument(List<Map<String, Object>> exceptionDocuments,
                                                         Map<String, Object> caseData) {
        //TODO check that document Id is unique and not duplicate in caseData
        List<Object> caseList = (List<Object>) caseData.get(SCANNED_DOCUMENTS);
        caseList.addAll(exceptionDocuments);
        return ImmutableMap.of(SCANNED_DOCUMENTS, caseList);
    }
}
