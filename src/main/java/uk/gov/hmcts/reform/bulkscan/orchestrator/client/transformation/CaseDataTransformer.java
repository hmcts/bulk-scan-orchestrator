package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.TransformationRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;

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
}
