package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import feign.FeignException;
import io.vavr.Value;
import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.lang.String.format;
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

    private final CoreCaseDataApi ccdApi;
    private CcdAuthenticatorFactory authFactory;

    public CallbackProcessor(CoreCaseDataApi ccdApi, CcdAuthenticatorFactory authFactory) {
        this.ccdApi = ccdApi;
        this.authFactory = authFactory;
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
                                    String jurisdiction,
                                    String caseTypeId,
                                    String caseRef,
                                    CaseDetails exceptionRecord) {
        CcdAuthenticator authenticator = authFactory.createForJurisdiction(jurisdiction);
        try {
            CaseDetails theCase = retrieveCase(caseRef, authenticator);
        } catch (FeignException e) {
            switch (e.status()) {
                case 404:
                    return error("Could not find case: %s", caseRef);
                default:
                    return error("Internal Error: Could not retrieve case: %s Error: %s", caseRef, e.status());
            }
        }
        return emptyList();
    }

    private CaseDetails retrieveCase(String caseRef, CcdAuthenticator authenticator) {
        return ccdApi.getCase(authenticator.getUserToken(), authenticator.getServiceToken(), caseRef);
    }

    private List<String> error(String errorFmt, Object arg) {
        return error(errorFmt, arg, null);
    }

    @NotNull
    private List<String> error(String errorFmt, Object arg1, Object arg2) {
        String message = format(errorFmt, arg1, arg2);
        log.error(message);
        return ImmutableList.of(message);
    }


}
