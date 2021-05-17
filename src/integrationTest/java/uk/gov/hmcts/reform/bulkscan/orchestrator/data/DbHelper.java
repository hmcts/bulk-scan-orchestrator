package uk.gov.hmcts.reform.bulkscan.orchestrator.data;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRowMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Profile("db-test")
@Component
public class DbHelper {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CallbackResultRowMapper callbackResultRowMapper;

    public DbHelper(
        NamedParameterJdbcTemplate jdbcTemplate,
        CallbackResultRowMapper callbackResultRowMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.callbackResultRowMapper = callbackResultRowMapper;
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM callback_result", new MapSqlParameterSource());
    }

    public List<CallbackResult> getAllCallbackResults() {
        return jdbcTemplate.query(
            "SELECT id, created_at, request_type, exception_record_id, case_id FROM callback_result",
            callbackResultRowMapper
        );
    }

    public UUID insertCallbackResultWithCreatedAt(NewCallbackResult callbackResult, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO callback_result (id, created_at, request_type, exception_record_id, case_id) "
                + "VALUES (:id, :createdAt, :requestType, :exceptionRecordId, :caseId)",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("createdAt", Timestamp.from(createdAt))
                .addValue("requestType", callbackResult.requestType.name())
                .addValue("exceptionRecordId", callbackResult.exceptionRecordId)
                .addValue("caseId", callbackResult.caseId)
        );
        return id;
    }
}
