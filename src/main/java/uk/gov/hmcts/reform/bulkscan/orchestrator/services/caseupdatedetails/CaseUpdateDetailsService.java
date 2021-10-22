package uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateDataClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
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
     *
     * @param service         service that should be called to get the data.
     * @param existingCase    CCD case to update.
     * @param exceptionRecord exception record that should be used to update the case.
     */
    public SuccessfulUpdateResponse getCaseUpdateData(
        String service,
        CaseDetails existingCase,
        ExceptionRecord exceptionRecord
    ) {
        return updateCase(service,requestCreator.create(exceptionRecord, existingCase));
    }

    /**
     * Retrieves data that should be used to update given case based on given envelope.
     *
     * @param service      service that should be called to get the data.
     * @param existingCase CCD case to update.
     * @param envelope     envelope that should be used to update the case.
     */
    public SuccessfulUpdateResponse getCaseUpdateData(
        String service,
        CaseDetails existingCase,
        Envelope envelope
    ) {
        return updateCase(service,requestCreator.create(envelope, existingCase));
    }

    private SuccessfulUpdateResponse updateCase(String service, CaseUpdateRequest request) {
        String s2sToken = s2sTokenGenerator.generate();
        String url = serviceConfigProvider.getConfig(service).getUpdateUrl();
        return caseUpdateDataClient.getCaseUpdateData(url, s2sToken, request);
    }
}
