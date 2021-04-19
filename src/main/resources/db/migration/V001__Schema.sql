CREATE TABLE envelopes (
  id UUID PRIMARY KEY,
  processor_envelope_id UUID NOT NULL,
  received_from_processor_at TIMESTAMP NOT NULL,
  case_ref VARCHAR(50) NOT NULL,
  legacy_case_ref VARCHAR(50) NOT NULL,
  po_box VARCHAR(50) NOT NULL,
  jurisdiction VARCHAR(50) NOT NULL,
  container VARCHAR(50) NOT NULL,
  zip_file_name VARCHAR(255) NOT NULL,
  form_type VARCHAR(50) NOT NULL,
  delivery_date TIMESTAMP NOT NULL,
  opening_date TIMESTAMP NOT NULL,
  classification VARCHAR(50) NOT NULL
);

CREATE TABLE documents (
  id UUID PRIMARY KEY,
  processor_envelope_id UUID NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  control_number VARCHAR(100),
  type VARCHAR(50) NOT NULL,
  sub_type VARCHAR(50) NOT NULL,
  scanned_at TIMESTAMP,
  uuid VARCHAR(50),
  delivery_date TIMESTAMP
);

CREATE TABLE payments (
  id UUID PRIMARY KEY,
  processor_envelope_id UUID NOT NULL,
  document_control_number VARCHAR(100),
  envelope_id UUID REFERENCES envelopes(id)
);

CREATE TABLE ocr_data_fields (
  id UUID PRIMARY KEY,
  processor_envelope_id UUID NOT NULL,
  name VARCHAR(50),
  value VARCHAR(50),
  envelope_id UUID REFERENCES envelopes(id)
);

CREATE TABLE ocr_data_validation_warnings (
  id UUID PRIMARY KEY,
  processor_envelope_id UUID NOT NULL,
  text VARCHAR(255),
  envelope_id UUID REFERENCES envelopes(id)
);

CREATE TABLE processed_envelopes (
  id UUID PRIMARY KEY,
  sent_to_processor_at TIMESTAMP NOT NULL,
  ccd_id VARCHAR(50),
  envelope_ccd_action VARCHAR(50),
  envelope_id UUID REFERENCES envelopes(id)
);
