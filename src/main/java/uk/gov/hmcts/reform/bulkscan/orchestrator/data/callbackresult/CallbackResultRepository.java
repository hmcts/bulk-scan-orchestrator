package uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CallbackResultRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CallbackResultRowMapper callbackResultRowMapper;

    public CallbackResultRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                    CallbackResultRowMapper callbackResultRowMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.callbackResultRowMapper = callbackResultRowMapper;
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

    public Optional<CallbackResult> findByExceptionRecordId(String exceptionRecordId) {
        try {
            CallbackResult callbackResult = jdbcTemplate.queryForObject(
                "SELECT * FROM callback_result WHERE exception_record_id = :exceptionRecordId",
                new MapSqlParameterSource("exceptionRecordId", exceptionRecordId),
                callbackResultRowMapper
            );
            return Optional.of(callbackResult);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<CallbackResult> findByCaseId(String caseId) {
        try {
            CallbackResult callbackResult = jdbcTemplate.queryForObject(
                "SELECT * FROM callback_result WHERE case_id = :caseId",
                new MapSqlParameterSource("caseId", caseId),
                callbackResultRowMapper
            );
            return Optional.of(callbackResult);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
