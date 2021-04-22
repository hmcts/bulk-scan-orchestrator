package uk.gov.hmcts.reform.bulkscan.orchestrator.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.CREATE_CASE;

@ActiveProfiles({"nosb", "db-test"})
@IntegrationTest
public class CallbackResultRepositoryTest {

    private static final String ER_ID_1 = "ER_ID_1";
    private static final String CASE_ID_1 = "CASE_ID_1";

    @Autowired private CallbackResultRepository repo;
    @Autowired private DbHelper dbHelper;

    @BeforeEach
    void setUp() {
        dbHelper.deleteAll();
    }

    @Test
    void should_save_create_case_result() {
        // given
        var newEnvelope = NewCallbackResult.createCaseRequest(ER_ID_1, CASE_ID_1);

        // when
        UUID id = repo.insert(newEnvelope);

        // then
        dbHelper.getAllCallbackResults();
        assertThat(dbHelper.getAllCallbackResults())
            .hasSize(1)
            .extracting(res -> tuple(res.id, res.requestType, res.exceptionRecordId, res.caseId))
            .containsExactly(
                tuple(id, CREATE_CASE, ER_ID_1, CASE_ID_1)
            );
    }

    @Test
    void should_save_attach_to_case_result() {
        // given
        var newEnvelope = NewCallbackResult.attachToCaseCaseRequest(ER_ID_1, CASE_ID_1);

        // when
        UUID id = repo.insert(newEnvelope);

        // then
        dbHelper.getAllCallbackResults();
        assertThat(dbHelper.getAllCallbackResults())
            .hasSize(1)
            .extracting(res -> tuple(res.id, res.requestType, res.exceptionRecordId, res.caseId))
            .containsExactly(
                tuple(id, ATTACH_TO_CASE, ER_ID_1, CASE_ID_1)
            );
    }
}
