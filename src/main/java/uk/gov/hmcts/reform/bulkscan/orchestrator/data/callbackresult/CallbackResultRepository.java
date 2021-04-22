package uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", matchIfMissing = true)
public class CallbackResultRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CallbackResultRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID insert(NewCallbackResult callbackResult) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO callback_result (id, request_type, exception_record_id, case_id, created_at) "
                + "VALUES (:id, :requestType, :exceptionRecordId, :caseId, CURRENT_TIMESTAMP)",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("requestType", callbackResult.requestType.name())
                .addValue("exceptionRecordId", callbackResult.exceptionRecordId)
                .addValue("caseId", callbackResult.caseId)
        );
        return id;
    }
}
