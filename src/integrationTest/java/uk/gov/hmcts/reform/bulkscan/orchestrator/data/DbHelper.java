package uk.gov.hmcts.reform.bulkscan.orchestrator.data;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Profile("db-test")
@Component
public class DbHelper {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CallbackResultRowMapper callbackResultRowMapper;

    public DbHelper(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.callbackResultRowMapper = new CallbackResultRowMapper();
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

    static class CallbackResultRowMapper implements RowMapper<CallbackResult> {

        @Override
        public CallbackResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CallbackResult(
                UUID.fromString(rs.getString("id")),
                rs.getTimestamp("created_at").toInstant(),
                RequestType.valueOf(rs.getString("request_type")),
                rs.getString("exception_record_id"),
                rs.getString("case_id")
            );
        }
    }
}
