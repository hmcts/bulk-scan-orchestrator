package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation;

import feign.FeignException;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.EnvelopeTransformer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

@Service
public class AutoCaseCreator {

    private static final Logger log = LoggerFactory.getLogger(AutoCaseCreator.class);

    private final EnvelopeTransformer envelopeTransformer;
    private final CcdApi ccdApi;
    private final ServiceConfigProvider serviceConfigProvider;
    private final CdamApiClient cdamApiClient;

    public AutoCaseCreator(
            EnvelopeTransformer envelopeTransformer,
            CcdApi ccdApi,
            ServiceConfigProvider serviceConfigProvider,
            CdamApiClient cdamApiClient
    ) {
        this.envelopeTransformer = envelopeTransformer;
        this.ccdApi = ccdApi;
        this.serviceConfigProvider = serviceConfigProvider;
        this.cdamApiClient = cdamApiClient;
    }

    public CaseCreationResult createCase(Envelope envelope) {
        String loggingContext = getLoggingContext(envelope);
        log.info("Started attempt to auto create a new case from envelope. {}", loggingContext);

        if (serviceConfigProvider.getConfig(envelope.container).getAutoCaseCreationEnabled()) {
            return createCaseIfDoesNotExist(envelope, loggingContext);
        } else {
            log.info("Automatic case creation is disabled for the service - skipping. {}", loggingContext);
            return CaseCreationResult.abortedWithoutFailure();
        }
    }

    private CaseCreationResult createCaseIfDoesNotExist(Envelope envelope, String loggingContext) {
        List<Long> caseIds = ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container);

        if (caseIds.isEmpty()) {
            return transformAndCreateCase(envelope, loggingContext);
        } else if (caseIds.size() == 1) {
            long caseId = caseIds.get(0);
            log.warn("Case already exists for envelope - skipping creation. Case ID: {}. {}", caseId, loggingContext);
            return CaseCreationResult.caseAlreadyExists(caseId);
        } else {
            log.error(
                "Multiple cases exist for envelope. Case Ids: [{}]. {}",
                Strings.join(caseIds, ','),
                loggingContext
            );

            return CaseCreationResult.unrecoverableFailure();
        }
    }

    private CaseCreationResult transformAndCreateCase(Envelope envelope, String loggingContext) {
        return envelopeTransformer.transformEnvelope(envelope)
            .map(resp -> resp.caseCreationDetails)
            .map(caseCreationDetails -> updateDocumentHash(envelope.jurisdiction, caseCreationDetails))
            .map(caseCreationDetails -> createCaseInCcd(caseCreationDetails, envelope, loggingContext))
            .getOrElseGet(failureType -> {
                    log.warn("Failed to auto create case, failure type {}, {}", failureType, loggingContext);
                    return failureType == EnvelopeTransformer.TransformationFailureType.UNRECOVERABLE
                        ? CaseCreationResult.unrecoverableFailure()
                        : CaseCreationResult.potentiallyRecoverableFailure();
                    }
            );
    }

    @SuppressWarnings("unchecked")
    private CaseCreationDetails updateDocumentHash(
            String jurisdiction,
            CaseCreationDetails caseCreationDetails
    ) {
        List<?> scannedDocuments =
                (List<?>)caseCreationDetails.caseData.get("scannedDocuments");

        if (scannedDocuments != null) {
            for (Object scannedDocumentValue : scannedDocuments) {
                Map scannedDocument = (Map)((Map) scannedDocumentValue).get("value");
                Map url = (Map)scannedDocument.get("url");
                String documentUrl = (String) url.get("document_url");
                String documentUuid = getDocumentUuid(documentUrl);
                String documentHash = cdamApiClient.getDocumentHash(jurisdiction, documentUuid);
                url.put("document_hash", documentHash);
            }
        }

        return caseCreationDetails;
    }

    private String getDocumentUuid(String documentUrl) {
        return documentUrl.substring(documentUrl.lastIndexOf("/") + 1);
    }

    private CaseCreationResult createCaseInCcd(
        CaseCreationDetails caseCreationDetails,
        Envelope envelope,
        String loggingContext
    ) {
        try {
            log.info("About to auto create a case in CCD from envelope. {}", loggingContext);
            long caseId = callCcdApiToCreateCase(caseCreationDetails, envelope, loggingContext);
            log.info("Auto created a case in CCD from envelope. Case Id: {}. {}", caseId, loggingContext);

            return CaseCreationResult.caseCreated(caseId);
        } catch (FeignException.UnprocessableEntity | FeignException.BadRequest ex) {
            log.error(
                "Received a response with status {} when trying to auto create a CCD case from an envelope. {}",
                ex.status(),
                loggingContext,
                ex
            );

            return CaseCreationResult.unrecoverableFailure();
        } catch (Exception ex) {
            log.error(
                "An error occurred when trying to auto create a case in CCD from envelope. {}",
                loggingContext,
                ex
            );

            return CaseCreationResult.potentiallyRecoverableFailure();
        }
    }

    private long callCcdApiToCreateCase(
        CaseCreationDetails caseCreationDetails,
        Envelope envelope,
        String loggingContext
    ) {
        return ccdApi.createCase(
            envelope.jurisdiction,
            caseCreationDetails.caseTypeId,
            caseCreationDetails.eventId,
            startEventResponse ->
                getCaseDataContent(
                    caseCreationDetails.caseData,
                    envelope.id,
                    startEventResponse.getEventId(),
                    startEventResponse.getToken()
                ),
            loggingContext
        );
    }

    private CaseDataContent getCaseDataContent(
        Map<String, Object> caseData,
        String envelopeId,
        String eventId,
        String eventToken
    ) {
        Map<String, Object> data = new HashMap<>(caseData);

        data.put(
            "bulkScanEnvelopes",
            singletonList(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, CaseAction.CREATE)))
        );

        return CaseDataContent
            .builder()
            .data(data)
            .event(Event
                .builder()
                .id(eventId)
                .summary("Case created")
                .description("Case created from envelope " + envelopeId)
                .build()
            )
            .eventToken(eventToken)
            .build();
    }

    private String getLoggingContext(Envelope envelope) {
        return format(
            "Envelope ID: %s. File name: %s. Service: %s.",
            envelope.id,
            envelope.zipFileName,
            envelope.container
        );
    }
}
