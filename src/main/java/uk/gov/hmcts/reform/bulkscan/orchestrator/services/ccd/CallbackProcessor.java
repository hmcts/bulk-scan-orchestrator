package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import feign.FeignException;
import io.vavr.Value;
import io.vavr.control.Validation;
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

    private static final String ATTACH_RECORD = "attachRecord";

    private final CoreCaseDataApi ccdApi;
    private final CcdAuthenticatorFactory authenticatorFactory;

    public CallbackProcessor(CoreCaseDataApi ccdApi, CcdAuthenticatorFactory authenticatorFactory) {
        this.ccdApi = ccdApi;
        this.authenticatorFactory = authenticatorFactory;
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
            .ap(this::attach)
            .getOrElseGet(Value::toJavaList);
    }

    private List<String> attach(String theType,
                                String anEventId,
                                String jurisdiction,
                                String caseTypeId,
                                String caseRef,
                                CaseDetails theCase) {
        try {
            CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);
            startAttachEvent(authenticator, caseRef, jurisdiction, caseTypeId);
        } catch (FeignException e) {
            log.error("Start event failed", e);
            return ImmutableList.of(format("Internal Error: start event call failed with %d", e.status()));
        }
        return emptyList();
    }

    private void startAttachEvent(CcdAuthenticator authenticator,
                                  String caseReference,
                                  String jurisdiction,
                                  String caseTypeId) {
        ccdApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseReference,
            ATTACH_RECORD
        );
    }

}
