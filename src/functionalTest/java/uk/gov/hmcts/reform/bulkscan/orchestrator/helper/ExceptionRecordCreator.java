package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;

@Service
public class ExceptionRecordCreator {

    private static final Logger log = LoggerFactory.getLogger(ExceptionRecordCreator.class);

    private static final String JURISDICTION = "BULKSCAN";
    private static final String CASE_TYPE_ID = "BULKSCAN_ExceptionRecord";
    private static final String CREATE_EXCEPTION_TYPE_ID = "createException";

    private final CcdAuthenticatorFactory ccdAuthenticatorFactory;
    private final CoreCaseDataApi coreCaseDataApi;

    public ExceptionRecordCreator(
        CcdAuthenticatorFactory ccdAuthenticatorFactory,
        CoreCaseDataApi coreCaseDataApi
    ) {
        this.ccdAuthenticatorFactory = ccdAuthenticatorFactory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public CaseDetails createExceptionRecord(
        Map<String, Object> dataMap
    ) {
        log.info("Creating new case");
        CcdAuthenticator authenticator = ccdAuthenticatorFactory.createForJurisdiction(JURISDICTION);

        StartEventResponse startEventResponse = startEventForCreateCase(authenticator);
        log.info("Started {} event for creating a new case", startEventResponse.getEventId());

        CaseDataContent caseDataContent = prepareCaseData(startEventResponse, dataMap);

        CaseDetails caseDetails = submitNewCase(authenticator, caseDataContent);
        log.info("Submitted {} event for creating a new case", startEventResponse.getEventId());

        return caseDetails;
    }

    private CaseDataContent prepareCaseData(
        StartEventResponse startEventResponse,
        Map<String, Object> dataMap
    ) {
        return CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(Event.builder()
                .id(CREATE_EXCEPTION_TYPE_ID)
                .summary("create new case")
                .description("create new case for tests")
                .build())
            .data(dataMap)
            .build();
    }

    private StartEventResponse startEventForCreateCase(CcdAuthenticator authenticator) {
        return coreCaseDataApi.startForCaseworker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            JURISDICTION,
            CASE_TYPE_ID,
            CREATE_EXCEPTION_TYPE_ID
        );
    }

    private CaseDetails submitNewCase(
        CcdAuthenticator authenticator,
        CaseDataContent caseDataContent
    ) {
        return coreCaseDataApi.submitForCaseworker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            JURISDICTION,
            CASE_TYPE_ID,
            true,
            caseDataContent
        );
    }
}
