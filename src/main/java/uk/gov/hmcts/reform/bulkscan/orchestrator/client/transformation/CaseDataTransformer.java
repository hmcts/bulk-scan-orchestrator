package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.TransformationRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

@Component
public class CaseDataTransformer {

    private final TransformationRequestCreator requestCreator;
    private final TransformationClient transformationClient;

    public CaseDataTransformer(
        TransformationRequestCreator requestCreator,
        TransformationClient transformationClient
    ) {
        this.requestCreator = requestCreator;
        this.transformationClient = transformationClient;
    }

    public SuccessfulTransformationResponse transformExceptionRecord(
        String baseUrl,
        ExceptionRecord exceptionRecord,
        String s2sToken
    ) {
        return transformationClient.transformCaseData(
            baseUrl,
            requestCreator.create(exceptionRecord),
            s2sToken
        );
    }

    public SuccessfulTransformationResponse transformEnvelope(
        String baseUrl,
        Envelope envelope,
        String s2sToken
    ) {
        return transformationClient.transformCaseData(
            baseUrl,
            requestCreator.create(envelope),
            s2sToken
        );
    }
}
