package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model;

import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Classification;

public interface CaseDataCreator {

    CaseDataContent createDataContent(
        Classification classification,
        String eventToken,
        boolean ignoreWarning
    );
}
