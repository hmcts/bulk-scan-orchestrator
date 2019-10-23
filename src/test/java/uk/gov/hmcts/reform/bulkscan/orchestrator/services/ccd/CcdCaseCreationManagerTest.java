package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;

@ExtendWith(MockitoExtension.class)
class CcdCaseCreationManagerTest {

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";
    private static final String SERVICE = "service";
    private static final long CASE_ID = 123;
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";
    private static final String CASE_REFERENCE_KEY = "caseReference";
    private static final Long CASE_REFERENCE_1 = 456L;
    private static final long CASE_REFERENCE_2 = 567L;

    @Mock
    private CcdCaseSubmitter ccdCaseSubmitter;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private CaseDetails caseDetails;

    @Mock
    private ServiceConfigItem configItem;

    private ExceptionRecord exceptionRecord;

    private CcdCaseCreationManager ccdCaseCreationManager;

    @BeforeEach
    void setUp() {
        ccdCaseCreationManager = new CcdCaseCreationManager(ccdCaseSubmitter, ccdApi);

        when(configItem.getService()).thenReturn("service");
        exceptionRecord = getExceptionRecord();
    }

    @Test
    void should_create_new_case_for_exception_record_if_it_does_not_exist() {
        // given
        when(ccdApi.getCaseRefsByBulkScanCaseReference(Long.toString(CASE_ID), SERVICE)).thenReturn(emptyList());
        ProcessResult processResult = new ProcessResult(
            ImmutableMap.<String, Object>builder()
                .build()
        );
        when(ccdCaseSubmitter
            .createNewCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID, caseDetails))
            .thenReturn(processResult);

        // when
        ProcessResult res =
            ccdCaseCreationManager.tryCreateNewCase(
                exceptionRecord,
                configItem,
                true,
                IDAM_TOKEN,
                USER_ID,
                caseDetails
            );

        // then
        assertThat(res).isEqualTo(processResult);
        verify(ccdCaseSubmitter).createNewCase(
            exceptionRecord,
            configItem,
            true,
            IDAM_TOKEN,
            USER_ID,
            caseDetails
        );
    }

    @Test
    void should_return_existing_case_for_exception_record() {
        // given
        when(ccdApi.getCaseRefsByBulkScanCaseReference(Long.toString(CASE_ID), SERVICE))
            .thenReturn(asList(CASE_REFERENCE_1));

        // when
        ProcessResult res =
            ccdCaseCreationManager.tryCreateNewCase(
                exceptionRecord,
                configItem,
                true,
                IDAM_TOKEN,
                USER_ID,
                caseDetails
            );

        // then
        assertThat(res.getModifiedFields().containsKey(CASE_REFERENCE_KEY)).isEqualTo(true);
        assertThat(res.getModifiedFields().get(CASE_REFERENCE_KEY))
            .isEqualTo(Long.toString(CASE_REFERENCE_1));
        verify(ccdCaseSubmitter, never()).createNewCase(any(), any(),anyBoolean(), anyString(), anyString(), any());
    }

    @Test
    void should_return_error_if_multiple_cases_exist_for_exception_record() {
        // given
        when(ccdApi.getCaseRefsByBulkScanCaseReference(Long.toString(CASE_ID), SERVICE))
            .thenReturn(asList(CASE_REFERENCE_1, CASE_REFERENCE_2));

        // when
        ProcessResult res =
            ccdCaseCreationManager.tryCreateNewCase(
                exceptionRecord,
                configItem,
                true,
                IDAM_TOKEN,
                USER_ID,
                caseDetails
            );

        // then
        assertThat(res.getModifiedFields()).isEmpty();
        assertThat(res.getErrors()).containsOnly(
            "Multiple cases (456, 567) found for the given bulk scan case reference: 123"
        );
        assertThat(res.getWarnings()).isEmpty();
        verify(ccdCaseSubmitter, never()).createNewCase(any(), any(),anyBoolean(), anyString(), anyString(), any());
    }

    private ExceptionRecord getExceptionRecord() {
        return new ExceptionRecord(
            Long.toString(CASE_ID),
            CASE_TYPE_ID,
            "po_box",
            "some jurisdiction",
            EXCEPTION,
            "form",
            now(),
            now(),
            emptyList(),
            emptyList()
        );
    }
}
