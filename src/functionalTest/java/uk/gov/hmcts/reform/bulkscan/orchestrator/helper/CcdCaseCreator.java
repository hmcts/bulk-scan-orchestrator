package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

@Service
public class CcdCaseCreator {

    private static final Logger log = LoggerFactory.getLogger(CcdCaseCreator.class);

    private static final String JURISDICTION = "BULKSCAN";
    private static final String CASE_TYPE_ID = "Bulk_Scanned";
    private static final String CREATE_CASE_EVENT_TYPE_ID = "createCase";

    private final CcdAuthenticatorFactory ccdAuthenticatorFactory;
    private final CoreCaseDataApi coreCaseDataApi;

    private final SupplementaryEvidenceMapper supplementaryEvidenceMapper;

    public CcdCaseCreator(
        CcdAuthenticatorFactory ccdAuthenticatorFactory,
        CoreCaseDataApi coreCaseDataApi,
        SupplementaryEvidenceMapper supplementaryEvidenceMapper
    ) {
        this.ccdAuthenticatorFactory = ccdAuthenticatorFactory;
        this.coreCaseDataApi = coreCaseDataApi;
        this.supplementaryEvidenceMapper = supplementaryEvidenceMapper;
    }

    public CaseDetails createCase(List<Document> documents, Instant deliveryDate) {
        return createCase(documents, deliveryDate, emptyMap());
    }

    public CaseDetails createCase(
        List<Document> documents,
        Instant deliveryDate,
        Map<String, Object> fieldsToOverwrite
    ) {
        log.info("Creating new case");
        CcdAuthenticator authenticator = ccdAuthenticatorFactory.createForJurisdiction(JURISDICTION);

        StartEventResponse startEventResponse = startEventForCreateCase(authenticator);
        log.info("Started {} event for creating a new case", startEventResponse.getEventId());

        CaseDataContent caseDataContent =
            prepareCaseData(startEventResponse, documents, deliveryDate, fieldsToOverwrite);

        CaseDetails caseDetails = submitNewCase(authenticator, caseDataContent);
        log.info("Submitted {} event for creating a new case", startEventResponse.getEventId());

        return caseDetails;
    }

    private CaseDataContent prepareCaseData(
        StartEventResponse startEventResponse,
        List<Document> documents,
        Instant deliveryDate,
        Map<String, Object> fieldsToOverwrite
    ) {
        SupplementaryEvidence supplementaryEvidence =
            supplementaryEvidenceMapper.map(emptyList(), null, envelope(documents, deliveryDate));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("evidenceHandled", supplementaryEvidence.evidenceHandled);
        eventData.put("scannedDocuments", supplementaryEvidence.scannedDocuments);

        String legacyId = "legacy-id-" + (long) (Math.random() * 100_000_000d);
        eventData.put("legacyId", legacyId);

        if (fieldsToOverwrite != null) {
            eventData.putAll(fieldsToOverwrite);
        }

        return CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(Event.builder()
                .id(CREATE_CASE_EVENT_TYPE_ID)
                .summary("create new case")
                .description("create new case for tests")
                .build())
            .data(eventData)
            .build();
    }

    private StartEventResponse startEventForCreateCase(CcdAuthenticator authenticator) {
        return coreCaseDataApi.startForCaseworker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            JURISDICTION,
            CASE_TYPE_ID,
            CREATE_CASE_EVENT_TYPE_ID
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
