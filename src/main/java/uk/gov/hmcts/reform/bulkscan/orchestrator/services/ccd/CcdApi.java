package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

/**
 * This class is intended to be a wrapper/adaptor/facade for the orchestrator -> CcdApi.
 * In theory this should make the calls to ccd both easier to manage and quicker to refactor.
 */
@Component
public class CcdApi {

    public static final String SEARCH_BY_LEGACY_ID_QUERY_FORMAT =
        "{\"query\": { \"match_phrase\" : { \"alias.previousServiceCaseReference\" : \"%s\" }}}";

    private final CoreCaseDataApi feignCcdApi;
    private final CcdAuthenticatorFactory authenticatorFactory;
    private final ServiceConfigProvider serviceConfigProvider;

    public CcdApi(
        CoreCaseDataApi feignCcdApi,
        CcdAuthenticatorFactory authenticator,
        ServiceConfigProvider serviceConfigProvider
    ) {
        this.feignCcdApi = feignCcdApi;
        this.authenticatorFactory = authenticator;
        this.serviceConfigProvider = serviceConfigProvider;
    }

    private CaseDetails retrieveCase(String caseRef, String jurisdiction) {
        CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);
        return feignCcdApi.getCase(authenticator.getUserToken(), authenticator.getServiceToken(), caseRef);
    }

    private StartEventResponse startAttachScannedDocs(String caseRef,
                                                      CcdAuthenticator authenticator,
                                                      String jurisdiction,
                                                      String caseTypeId) {
        return feignCcdApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseRef,
            "attachScannedDocs"
        );
    }

    @Nonnull
    StartEventResponse startAttachScannedDocs(CaseDetails theCase) {
        String caseRef = String.valueOf(theCase.getId());
        try {
            CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(theCase.getJurisdiction());
            return startAttachScannedDocs(caseRef, authenticator, theCase.getJurisdiction(), theCase.getCaseTypeId());
        } catch (FeignException e) {
            throw new CallbackException(
                format("Internal Error: start event call failed case: %s Error: %s", caseRef, e.status()), e
            );
        }
    }

    @Nonnull
    @SuppressWarnings("squid:S1135")
    CaseDetails getCase(String caseRef, String jurisdiction) {
        try {
            //TODO: RPE-823 merge with `CaseRetriever` to a consistent api adaptor
            return retrieveCase(caseRef, jurisdiction);
        } catch (FeignException e) {
            switch (e.status()) {
                case 404: throw new CaseNotFoundException("Could not find case: " + caseRef, e);
                case 400: throw new InvalidCaseIdException("Invalid case ID: " + caseRef, e);
                default: throw new CallbackException(
                    format("Internal Error: Could not retrieve case: %s Error: %s", caseRef, e.status()),
                    e
                );
            }
        }
    }

    List<Long> getCaseRefsByLegacyId(String legacyId, String service) {
        ServiceConfigItem serviceConfig = serviceConfigProvider.getConfig(service);
        String jurisdiction = serviceConfig.getJurisdiction();
        String caseTypeIdsStr = String.join(",", serviceConfig.getCaseTypeIds());
        CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);

        SearchResult searchResult = feignCcdApi.searchCases(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            caseTypeIdsStr,
            String.format(SEARCH_BY_LEGACY_ID_QUERY_FORMAT, legacyId)
        );

        return searchResult
            .getCases()
            .stream()
            .map(CaseDetails::getId)
            .collect(toList());
    }

    void attachExceptionRecord(CaseDetails theCase,
                               Map<String, Object> data,
                               String eventSummary,
                               StartEventResponse event) {
        String caseRef = String.valueOf(theCase.getId());
        String jurisdiction = theCase.getJurisdiction();
        String caseTypeId = theCase.getCaseTypeId();
        try {
            CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);
            attachCall(caseRef,
                authenticator,
                data,
                event.getToken(),
                jurisdiction,
                caseTypeId,
                Event.builder().summary(eventSummary).id(event.getEventId()).build());
        } catch (FeignException e) {
            throw new CallbackException(
                format("Internal Error: submitting attach file event failed case: %s Error: %s", caseRef, e.status()),
                e
            );
        }
    }

    private void attachCall(String caseRef,
                            CcdAuthenticator authenticator,
                            Map<String, Object> data,
                            String eventToken,
                            String jurisdiction,
                            String caseTypeId,
                            Event eventInfo) {
        feignCcdApi.submitEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseRef,
            true,
            CaseDataContent.builder()
                .data(data)
                .event(eventInfo)
                .eventToken(eventToken)
                .build()
        );
    }

    public CcdAuthenticator authenticateJurisdiction(String jurisdiction) {
        return authenticatorFactory.createForJurisdiction(jurisdiction);
    }

    public StartEventResponse startEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseTypeId,
        String caseRef,
        String eventTypeId
    ) {
        return  caseRef == null
            ? feignCcdApi.startForCaseworker(
                authenticator.getUserToken(),
                authenticator.getServiceToken(),
                authenticator.getUserDetails().getId(),
                jurisdiction,
                caseTypeId,
                eventTypeId
            )
            : feignCcdApi.startEventForCaseWorker(
                authenticator.getUserToken(),
                authenticator.getServiceToken(),
                authenticator.getUserDetails().getId(),
                jurisdiction,
                caseTypeId,
                caseRef,
                eventTypeId
            );
    }

    public CaseDetails submitEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseTypeId,
        String caseRef,
        CaseDataContent caseDataContent
    ) {
        return caseRef == null
            ? feignCcdApi.submitForCaseworker(
                authenticator.getUserToken(),
                authenticator.getServiceToken(),
                authenticator.getUserDetails().getId(),
                jurisdiction,
                caseTypeId,
                true,
                caseDataContent
            )
            : feignCcdApi.submitEventForCaseWorker(
                authenticator.getUserToken(),
                authenticator.getServiceToken(),
                authenticator.getUserDetails().getId(),
                jurisdiction,
                caseTypeId,
                caseRef,
                true,
                caseDataContent
            );
    }
}
