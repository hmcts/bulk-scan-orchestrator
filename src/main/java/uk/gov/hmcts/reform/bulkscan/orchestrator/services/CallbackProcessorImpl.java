package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.lang.String.format;

@Service
public class CallbackProcessorImpl implements CallbackProcessor {
    @Override
    public List<String> process(String type, String eventId, CaseDetails caseDetails) {
        if( "attachToCaseReference".equals(type)) {
            return ImmutableList.of();
        }else{
            return ImmutableList.of(format("Internal Error: invalid type supplied: %s",type));
        }
    }
}
