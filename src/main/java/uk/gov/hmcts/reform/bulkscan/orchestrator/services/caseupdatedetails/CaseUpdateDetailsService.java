package uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails;

import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateDataClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

public class CaseUpdateDetailsService {

    private final AuthTokenGenerator s2sTokenGenerator;
    private final CaseUpdateDataClient caseUpdateDataClient;
    private final CaseUpdateRequestCreator requestCreator;
    private final ServiceConfigProvider serviceConfigProvider;

    public CaseUpdateDetailsService(
        AuthTokenGenerator s2sTokenGenerator,
        CaseUpdateDataClient caseUpdateDataClient,
        CaseUpdateRequestCreator requestCreator,
        ServiceConfigProvider serviceConfigProvider
    ) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.caseUpdateDataClient = caseUpdateDataClient;
        this.requestCreator = requestCreator;
        this.serviceConfigProvider = serviceConfigProvider;
    }

    /**
     * Retrieves data that should be used to update given case based on given exception record.
     */
    public SuccessfulUpdateResponse getCaseUpdateData(
        String service,
        CaseDetails existingCase,
        ExceptionRecord exceptionRecord
    ) {
        String s2sToken = s2sTokenGenerator.generate();
        String url = serviceConfigProvider.getConfig(service).getUpdateUrl();
        CaseUpdateRequest request = requestCreator.create(exceptionRecord, existingCase, false);

        return caseUpdateDataClient.getCaseUpdateData(url, s2sToken, request);
    }
}
