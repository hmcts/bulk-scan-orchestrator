package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateClient;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;

class CcdCaseUpdaterTest {

    private CcdCaseUpdater ccdCaseUpdater;

    @Mock
    private CaseUpdateClient caseUpdateClient;

    @Mock
    private AuthTokenGenerator authTokenGenerator;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    @Mock
    private ExceptionRecordFinalizer exceptionRecordFinalizer;

    @BeforeEach
    void setUp() {
        ccdCaseUpdater = new CcdCaseUpdater(
            caseUpdateClient,
            authTokenGenerator,
            coreCaseDataApi,
            exceptionRecordFinalizer
        );
    }

    @Test
    void updateCase() {
    }
}
