package uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Component
public class CallbackResultRowMapper implements RowMapper<CallbackResult> {

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
