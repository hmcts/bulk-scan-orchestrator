package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import io.vavr.Value;
import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;

import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAboutToSubmit;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.log;

@Service
public class CallbackProcessor {

    private CcdAuthenticatorFactory authFactory;
    private final CallbackCcdApi ccdApi;

    public CallbackProcessor(CallbackCcdApi ccdApi, CcdAuthenticatorFactory authFactory) {
        this.authFactory = authFactory;
        this.ccdApi = ccdApi;
    }

    public List<String> process(String eventType, String eventId, CaseDetails caseDetails) {
        return Validation
            .combine(
                isAttachEvent(eventType),
                isAboutToSubmit(eventId),
                hasJurisdiction(caseDetails),
                hasCaseTypeId(caseDetails),
                hasCaseReference(caseDetails),
                hasCaseDetails(caseDetails)
            )
            .ap(this::attachCase)
            .getOrElseGet(Value::toJavaList);
    }

    private List<String> attachCase(String theType,
                                    String anEventId,
                                    String exceptionRecordJurisdiction,
                                    String caseTypeId,
                                    String caseRef,
                                    CaseDetails exceptionRecord) {
        try {
            attachCase(exceptionRecordJurisdiction, caseRef);
            return success();
        } catch (CallbackException e) {
            return createErrorList(e);
        }
    }

    @NotNull
    private void attachCase(String exceptionRecordJurisdiction, String caseRef) {
        CcdAuthenticator authenticator = authFactory.createForJurisdiction(exceptionRecordJurisdiction);
        CaseDetails theCase = ccdApi.getCase(caseRef, authenticator);
        StartEventResponse event = ccdApi.startAttachScannedDocs(caseRef, authenticator, theCase);
    }

    @NotNull
    private List<String> createErrorList(CallbackException e) {
        String message = e.getMessage();
        log.error(message, e);
        return ImmutableList.of(message);
    }

    @NotNull
    private List<String> success() {
        return emptyList();
    }
}
