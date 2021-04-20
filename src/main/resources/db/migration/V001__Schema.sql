CREATE TABLE callback_result (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  request_type VARCHAR(50) NOT NULL
  exception_record_id VARCHAR(50) NOT NULL
  case_id VARCHAR(50)
);
