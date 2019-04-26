package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static org.assertj.core.api.Assertions.assertThat;

@Service
public class CaseSearcher {

    private final CcdAuthenticatorFactory factory;

    private final CoreCaseDataApi coreCaseDataApi;

    public CaseSearcher(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public List<CaseDetails> search(
        String jurisdiction,
        String caseTypeId,
        Map<String, String> searchCriteria
    ) {
        // not including in try catch to fast fail the method
        CcdAuthenticator authenticator = factory.createForJurisdiction(jurisdiction);

        sleepUninterruptibly(5, TimeUnit.SECONDS);

        SearchResult searchResult = coreCaseDataApi.searchCases(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            "Bulk_Scanned",
            "{ \"query\": { \"match_all\": {} }, \"size\": 1, \"_source\": [\"jurisdiction\", \"data.firstName\"]}"
        );

        //    "{ \"query\":{ \"match\":{ \"data.poBox\":\"TESTPO\"}}}"

        //    "{ \"query\": { \"match_all\": {} }, \"size\": 50}"

        int elasticSearchResultCount = searchResult.getCases().size();

        assertThat(searchResult.getTotal()).isEqualTo(-1);

        assertThat(searchResult.getCases().get(0).getData()).isEqualTo(ImmutableMap.of());

        List<CaseDetails> result = coreCaseDataApi.searchForCaseworker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            searchCriteria
        );

        assertThat(elasticSearchResultCount)
            .as("Elasticsearch result should be the same")
            .isEqualTo(result.size());

        return result;
    }
}
