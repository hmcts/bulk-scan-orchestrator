package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

@Component
public class SupplementaryEvidenceWithOcrUpdater {
    private static final Logger log = LoggerFactory.getLogger(SupplementaryEvidenceWithOcrUpdater.class);

    private final ServiceConfigProvider serviceConfigProvider;
    private final CcdApi ccdApi;
    private final CcdCaseUpdater ccdCaseUpdater;

    public SupplementaryEvidenceWithOcrUpdater(
        ServiceConfigProvider serviceConfigProvider,
        CcdApi ccdApi,
        CcdCaseUpdater ccdCaseUpdater
    ) {
        this.serviceConfigProvider = serviceConfigProvider;
        this.ccdApi = ccdApi;
        this.ccdCaseUpdater = ccdCaseUpdater;
    }

    public Optional<ErrorsAndWarnings> updateSupplementaryEvidenceWithOcr(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef,
        boolean ignoreWarnings
    ) {
        ServiceConfigItem serviceConfigItem = getServiceConfig(callBackEvent.service);

        CaseDetails targetCase = ccdApi.getCase(targetCaseCcdRef, callBackEvent.exceptionRecordJurisdiction);

        return ccdCaseUpdater.updateCase(
            callBackEvent.exceptionRecord,
            serviceConfigItem.getService(),
            ignoreWarnings,
            callBackEvent.idamToken,
            callBackEvent.userId,
            targetCaseCcdRef,
            targetCase.getCaseTypeId()
        );
    }

    private ServiceConfigItem getServiceConfig(String service) {
        return Try.of(() -> serviceConfigProvider.getConfig(service))
            .filter(item -> !Strings.isNullOrEmpty(item.getUpdateUrl()))
            .getOrElseThrow(() -> new CallbackException("Update URL is not configured"));
    }
}
