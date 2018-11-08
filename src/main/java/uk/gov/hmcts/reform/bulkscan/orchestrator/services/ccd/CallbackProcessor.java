package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import feign.FeignException;
import io.vavr.Value;
import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachToCaseEvent;

@Service
public class CallbackProcessor {
    private static final Logger log = LoggerFactory.getLogger(CallbackProcessor.class);
    private final CoreCaseDataApi ccdApi;
    private final CcdAuthenticatorFactory authFactory;

    public CallbackProcessor(CoreCaseDataApi ccdApi, CcdAuthenticatorFactory authFactory) {
        this.ccdApi = ccdApi;
        this.authFactory = authFactory;
    }

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
                                    String jurisdiction,
                                    String caseTypeId,
                                    String caseRef,
                                    CaseDetails exceptionRecord) {
        CcdAuthenticator authenticator = authFactory.createForJurisdiction(jurisdiction);
        try {
            // TODO: use `CaseRetriever`
            retrieveCase(caseRef, authenticator);
        } catch (FeignException e) {
            if (e.status() == 404) {
                return error("Could not find case: %s", caseRef);
            } else {
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
