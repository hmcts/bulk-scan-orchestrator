package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import io.vavr.control.Try;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

@Component
public class SupplementaryEvidenceWithOcrUpdater {
    private final ServiceConfigProvider serviceConfigProvider;
    private final CcdCaseUpdater ccdCaseUpdater;

    public SupplementaryEvidenceWithOcrUpdater(
        ServiceConfigProvider serviceConfigProvider,
        CcdCaseUpdater ccdCaseUpdater
    ) {
        this.serviceConfigProvider = serviceConfigProvider;
        this.ccdCaseUpdater = ccdCaseUpdater;
    }

    public Optional<ErrorsAndWarnings> updateSupplementaryEvidenceWithOcr(
        AttachToCaseEventData callBackEvent,
        CaseDetails targetCase,
        boolean ignoreWarnings
    ) {
        ServiceConfigItem serviceConfigItem = getServiceConfig(callBackEvent.service);

        return ccdCaseUpdater.updateCase(
            callBackEvent.exceptionRecord,
            serviceConfigItem.getService(),
            ignoreWarnings,
            callBackEvent.idamToken,
            callBackEvent.userId,
            Long.toString(targetCase.getId()),
            targetCase.getCaseTypeId()
        );
    }

    private ServiceConfigItem getServiceConfig(String service) {
        return Try.of(() -> serviceConfigProvider.getConfig(service))
            .filter(item -> !Strings.isNullOrEmpty(item.getUpdateUrl()))
            .getOrElseThrow(() -> new CallbackException("Update URL is not configured"));
    }
}
