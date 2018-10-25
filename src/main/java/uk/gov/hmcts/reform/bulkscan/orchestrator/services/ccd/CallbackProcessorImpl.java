package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CallbackTypes;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.lang.String.format;

@Service
public class CallbackProcessorImpl implements CallbackProcessor {

    @Override
    public List<String> process(String type, String eventId, CaseDetails caseDetails) {
        if (isAttachEvent(type)) {
            if (CallbackTypes.ABOUT_TO_SUBMIT.equals(eventId)) {
                return ImmutableList.of();
            } else {
                return ImmutableList.of(format("Internal Error: : event-id: %s invalid", eventId));
            }

        } else {
            return ImmutableList.of(format("Internal Error: invalid type supplied: %s", type));
        }
    }

    private boolean isAttachEvent(String type) {
        return "attach_case".equals(type);
    }
}
