package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasServiceNameInCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateNewCaseEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;

@Service
public class CreateCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseCallbackService.class);

    private static final String EXCEPTION_RECORD_REFERENCE = "bulkScanCaseReference";

    private final CreateCaseValidator validator;
    private final ServiceConfigProvider serviceConfigProvider;
    private final TransformationClient transformationClient;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final IPaymentsPublisher paymentsPublisher;
    private final CoreCaseDataApi ccdApi;

    public CreateCaseCallbackService(
        CreateCaseValidator validator,
        ServiceConfigProvider serviceConfigProvider,
        TransformationClient transformationClient,
        AuthTokenGenerator s2sTokenGenerator,
        IPaymentsPublisher paymentsPublisher,
        CoreCaseDataApi ccdApi
    ) {
        this.validator = validator;
        this.serviceConfigProvider = serviceConfigProvider;
        this.transformationClient = transformationClient;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.paymentsPublisher = paymentsPublisher;
        this.ccdApi = ccdApi;
    }

    /**
     * Create case record from exception case record.
     *
     * @return Either list of errors or map of changes - new case reference
     */
    public ProcessResult process(CcdCallbackRequest request, String idamToken, String userId) {
        Validation<String, Void> canAccess = assertAllowToAccess(request.getCaseDetails(), request.getEventId());

        if (canAccess.isInvalid()) {
            // log happens in assertion method
            return new ProcessResult(emptyList(), singletonList(canAccess.getError()));
        }

        // already validated in mandatory section ^
        ServiceConfigItem serviceConfigItem = getServiceConfig(request.getCaseDetails()).get();

        CaseDetails exceptionRecordData = request.getCaseDetails();

        ProcessResult result = validator
            .getValidation(exceptionRecordData)
            .map(exceptionRecord -> createNewCase(
                exceptionRecord,
                serviceConfigItem,
                request.isIgnoreWarnings(),
                idamToken,
                userId,
                exceptionRecordData
            ))
            .mapError(Seq::asJava)
            .getOrElseGet(errors -> new ProcessResult(emptyList(), errors));

        if (!result.getWarnings().isEmpty()) {
            log.warn(
                "Warnings found for {} during callback process:\n  - {}",
                serviceConfigItem.getService(),
                String.join("\n  - ", result.getWarnings())
            );
        }

        if (!result.getErrors().isEmpty()) {
            log.error(
                "Errors found for {} during callback process:\n  - {}",
                serviceConfigItem.getService(),
                String.join("\n  - ", result.getErrors())
            );
        }

        return result;
    }

    private Validation<String, Void> assertAllowToAccess(CaseDetails caseDetails, String eventId) {
        return validator.mandatoryPrerequisites(
            () -> isCreateNewCaseEvent(eventId),
            () -> getServiceConfig(caseDetails).map(item -> null)
        );
    }

    private Validation<String, ServiceConfigItem> getServiceConfig(CaseDetails caseDetails) {
        return hasServiceNameInCaseTypeId(caseDetails).flatMap(service -> Try
            .of(() -> serviceConfigProvider.getConfig(service))
            .toValidation()
            .mapError(Throwable::getMessage)
        )
            .filter(item -> !Strings.isNullOrEmpty(item.getTransformationUrl()))
            .getOrElse(Validation.invalid("Transformation URL is not configured"));
    }

    @SuppressWarnings("unchecked")
    private ProcessResult createNewCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId,
        CaseDetails exceptionRecordData
    ) {
        log.info(
            "Start creating new case for {} from exception record {}",
            configItem.getService(),
            exceptionRecord.id
        );

        try {
            String s2sToken = s2sTokenGenerator.generate();

            SuccessfulTransformationResponse transformationResponse = transformationClient.transformExceptionRecord(
                configItem.getTransformationUrl(),
                exceptionRecord,
                s2sToken
            );

            if (!ignoreWarnings && !transformationResponse.warnings.isEmpty()) {
                // do not log warnings
                return new ProcessResult(transformationResponse.warnings, emptyList());
            }

            log.info(
                "Successfully transformed exception record for {} from exception record {}",
                configItem.getService(),
                exceptionRecord.id
            );

            checkBulkScanReferenceIsSet(
                (Map<String, ?>) transformationResponse.caseCreationDetails.caseData,
                exceptionRecord.id
            );

            long newCaseId = createNewCaseInCcd(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord,
                transformationResponse.caseCreationDetails
            );

            log.info(
                "Successfully created new case for {} with case ID {} from exception record {}",
                configItem.getService(),
                newCaseId,
                exceptionRecord.id
            );

            handlePayments(exceptionRecordData, newCaseId);

            return new ProcessResult(
                ImmutableMap.<String, Object>builder()
                    .put(CASE_REFERENCE, Long.toString(newCaseId))
                    .put(DISPLAY_WARNINGS, YesNoFieldValues.NO)
                    .put(OCR_DATA_VALIDATION_WARNINGS, emptyList())
                    .build()
            );
        } catch (InvalidCaseDataException exception) {
            if (BAD_REQUEST.equals(exception.getStatus())) {
                throw exception;
            } else {
                return new ProcessResult(exception.getResponse().warnings, exception.getResponse().errors);
            }
        } catch (Exception exception) {
            log.error(
                "Failed to create exception for service {} and exception record {}",
                configItem.getService(),
                exceptionRecord.id,
                exception
            );

            return new ProcessResult(emptyList(), singletonList("Internal error. " + exception.getMessage()));
        }
    }

    private void handlePayments(CaseDetails exceptionRecord, long newCaseId) {

        boolean containsPayments =
            Objects.equals(
                exceptionRecord.getData().get(ExceptionRecordFields.CONTAINS_PAYMENTS),
                YesNoFieldValues.YES
            );

        if (containsPayments) {

            String envelopeId = exceptionRecord.getData().get(ExceptionRecordFields.ENVELOPE_ID).toString();
            String jurisdiction = exceptionRecord.getData().get(ExceptionRecordFields.PO_BOX_JURISDICTION).toString();

            log.info(
                "Sending payment update message. ER id: {}",
                exceptionRecord.getId()
            );

            paymentsPublisher.send(
                new UpdatePaymentsCommand(
                    Long.toString(exceptionRecord.getId()),
                    Long.toString(newCaseId),
                    envelopeId,
                    jurisdiction
                )
            );
        } else {
            log.info(
                "Exception record has no payments, not sending update command. ER id: {}",
                exceptionRecord.getId()
            );
        }
    }

    private long createNewCaseInCcd(
        String idamToken,
        String s2sToken,
        String userId,
        ExceptionRecord exceptionRecord,
        CaseCreationDetails caseCreationDetails
    ) {
        StartEventResponse eventResponse = ccdApi.startForCaseworker(
            idamToken,
            s2sToken,
            userId,
            exceptionRecord.poBoxJurisdiction,
            caseCreationDetails.caseTypeId,
            // when onboarding remind services to not configure about to submit callback for this event
            caseCreationDetails.eventId
        );

        return ccdApi.submitForCaseworker(
            idamToken,
            s2sToken,
            userId,
            exceptionRecord.poBoxJurisdiction,
            caseCreationDetails.caseTypeId,
            true,
            CaseDataContent
                .builder()
                .caseReference(exceptionRecord.id)
                .data(caseCreationDetails.caseData)
                .event(Event
                    .builder()
                    .id(eventResponse.getEventId())
                    .summary("Case created")
                    .description("Case created from exception record ref " + exceptionRecord.id)
                    .build()
                )
                .eventToken(eventResponse.getToken())
                .build()
        ).getId();
    }

    private void checkBulkScanReferenceIsSet(Map<String, ?> caseData, String exceptionRecordId) {
        if (!caseData.containsKey(EXCEPTION_RECORD_REFERENCE)) {
            log.error(
                "Transformation did not set '{}' with exception record id {}",
                EXCEPTION_RECORD_REFERENCE,
                exceptionRecordId
            );
        } else if (!caseData.get(EXCEPTION_RECORD_REFERENCE).equals(exceptionRecordId)) {
            log.error(
                "Transformation did not set exception record reference correctly. Actual: {}, expected: {}",
                caseData.get(EXCEPTION_RECORD_REFERENCE),
                exceptionRecordId
            );
        }
    }
}
