package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers.CreateExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CaseSearcher {

    private final CcdAuthenticatorFactory factory;

    private final CoreCaseDataApi coreCaseDataApi;
    private static final Logger log = LoggerFactory.getLogger(CaseSearcher.class);

    public CaseSearcher(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    // only used in tests
    public Optional<CaseDetails> findExceptionRecord(String poBox, String container) {
        String caseTypeId = container.toUpperCase() + "_" + CreateExceptionRecord.CASE_TYPE;
        return search(
            SampleData.JURSIDICTION,
            caseTypeId,
            ImmutableMap.of("case.poBox", poBox)
        ).stream().findFirst();
    }

    // only used in tests. single source code call - only used in tests too
    public List<CaseDetails> search(
        String jurisdiction,
        String caseTypeId,
        Map<String, String> searchCriteria
    ) {
        // not including in try catch to fast fail the method
        CcdAuthenticator authenticator = factory.createForJurisdiction(jurisdiction);

        return coreCaseDataApi.searchForCaseworker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserId(),
            jurisdiction,
            caseTypeId,
            searchCriteria
        );
    }

    public List<CaseDetails> searchByEnvelopeId(
        String jurisdiction,
        String caseTypeId,
        String envelopeId
    ) {
        CcdAuthenticator authenticator = factory.createForJurisdiction(jurisdiction);

        SearchResult searchResult = coreCaseDataApi.searchCases(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            caseTypeId,
            "{\"query\":{\"match_phrase\":{\"data.bulkScanEnvelopes.value.id\":\"" + envelopeId + "\"}}}"
        );

        return searchResult.getCases();
    }
}
