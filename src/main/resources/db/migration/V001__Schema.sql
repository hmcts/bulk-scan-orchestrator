CREATE TABLE create_case_request (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  exception_record_id VARCHAR(50) NOT NULL
);

CREATE TABLE create_case_result (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  ccd_id VARCHAR(50),
  errors VARCHAR(255),
  warnings VARCHAR(255),
  request_id UUID REFERENCES create_case_request(id)
);

CREATE TABLE attach_to_case_request (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  exception_record_id VARCHAR(50) NOT NULL,
  target_case_ref VARCHAR(50)
);

CREATE TABLE attach_to_case_result (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  errors VARCHAR(255),
  warnings VARCHAR(255),
  request_id UUID REFERENCES attach_to_case_request(id)
);
