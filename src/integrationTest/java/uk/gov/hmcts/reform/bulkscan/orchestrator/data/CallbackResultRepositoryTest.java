package uk.gov.hmcts.reform.bulkscan.orchestrator.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.CREATE_CASE;

@ActiveProfiles({"nosb", "db-test"})
@IntegrationTest
public class CallbackResultRepositoryTest {

    private static final String ER_ID_1 = "ER_ID_1";
    private static final String ER_ID_2 = "ER_ID_2";
    private static final String CASE_ID_1 = "CASE_ID_1";
    private static final String CASE_ID_2 = "CASE_ID_2";
    private static final NewCallbackResult NEW_CALLBACK_RESULT_1 =
        NewCallbackResult.attachToCaseCaseRequest(ER_ID_1, CASE_ID_1);
    private static final NewCallbackResult NEW_CALLBACK_RESULT_2 =
        NewCallbackResult.attachToCaseCaseRequest(ER_ID_2, CASE_ID_2);

    @Autowired private CallbackResultRepository repo;
    @Autowired private DbHelper dbHelper;

    @BeforeEach
    void setUp() {
        dbHelper.deleteAll();
    }

    @Test
    void should_save_create_case_result() {
        // given
        var newCallbackResult = NewCallbackResult.createCaseRequest(ER_ID_1, CASE_ID_1);

        // when
        UUID id = repo.insert(newCallbackResult);

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
        var newCallbackResult = NewCallbackResult.attachToCaseCaseRequest(ER_ID_1, CASE_ID_1);

        // when
        UUID id = repo.insert(newCallbackResult);

        // then
        dbHelper.getAllCallbackResults();
        assertThat(dbHelper.getAllCallbackResults())
            .hasSize(1)
            .extracting(res -> tuple(res.id, res.requestType, res.exceptionRecordId, res.caseId))
            .containsExactly(
                tuple(id, ATTACH_TO_CASE, ER_ID_1, CASE_ID_1)
            );
    }

    @Test
    void should_find_by_case_id() {
        // given
        var createdAt1 = Instant.now();
        var createdAt2 = Instant.now();
        var id1 = dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_1, createdAt1);
        dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_2, createdAt2);

        // when
        var caseResults = repo.findByCaseId(CASE_ID_1);

        // then
        assertThat(caseResults.isEmpty()).isFalse();
        assertThat(caseResults)
            .extracting(res -> tuple(res.id, res.createdAt, res.requestType, res.exceptionRecordId, res.caseId))
            .containsExactly(tuple(id1, createdAt1, ATTACH_TO_CASE, ER_ID_1, CASE_ID_1));
    }

    @Test
    void should_find_by_exception_record_id() {
        // given
        var createdAt1 = Instant.now();
        var createdAt2 = Instant.now();
        var id1 = dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_1, createdAt1);
        dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_2, createdAt2);

        // when
        var caseResultOpt = repo.findByExceptionRecordId(ER_ID_1);

        // then
        assertThat(caseResultOpt)
            .extracting(res -> tuple(res.id, res.createdAt, res.requestType, res.exceptionRecordId, res.caseId))
            .containsExactly(tuple(id1, createdAt1, ATTACH_TO_CASE, ER_ID_1, CASE_ID_1));
    }

    @Test
    void should_not_find_by_non_existing_case_id() {
        // given
        var createdAt1 = Instant.now();
        var createdAt2 = Instant.now();
        dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_1, createdAt1);
        dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_2, createdAt2);

        // when
        var caseResults = repo.findByCaseId("WRONG_CASE_ID");

        // then
        assertThat(caseResults.isEmpty()).isTrue();
    }

    @Test
    void should_not_find_by_non_existing_exception_record_id() {
        // given
        var createdAt1 = Instant.now();
        var createdAt2 = Instant.now();
        dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_1, createdAt1);
        dbHelper.insertCallbackResultWithCreatedAt(NEW_CALLBACK_RESULT_2, createdAt2);

        // when
        var caseResults = repo.findByExceptionRecordId("WRONG_ER_ID");

        // then
        assertThat(caseResults.isEmpty()).isTrue();
    }
}
