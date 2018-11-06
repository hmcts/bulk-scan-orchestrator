package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.Value;
import io.vavr.control.Validation;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAboutToSubmit;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.isAttachEvent;

@Service
public class CallbackProcessor {

    public List<String> process(String eventType, String eventId, CaseDetails caseDetails) {
        return Validation
            .combine(
                isAttachEvent(eventType),
                isAboutToSubmit(eventId),
                hasCaseDetails(caseDetails)
            )
            .ap((theType, anEventId, theCase) -> attach(theCase))
            .getOrElseGet(Value::toJavaList);
    }


    @SuppressWarnings({"squid:S1172", "squid:S1135"})
    //TODO these are for the validations of the incoming request and is a WIP
    private List<String> attach(CaseDetails caseDetails) {
        return emptyList();
    }

}
