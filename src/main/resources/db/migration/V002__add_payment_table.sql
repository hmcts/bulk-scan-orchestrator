CREATE TABLE payments(
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  envelope_id VARCHAR(50) NOT NULL,
  ccd_reference VARCHAR(50) NOT NULL,
  jurisdiction VARCHAR(50) NOT NULL,
  service VARCHAR(50) NOT NULL,
  po_box VARCHAR(50) NOT NULL,
  is_exception_record BOOLEAN NOT NULL,
  status VARCHAR(50) NOT NULL
);

CREATE TABLE payments_data (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  payment_id UUID REFERENCES payments(id),
  document_control_number VARCHAR(50) NOT NULL
);

CREATE TABLE update_payments(
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  exception_record_ref VARCHAR(50) NOT NULL,
  new_case_ref VARCHAR(50) NOT NULL,
  envelope_id VARCHAR(50) NOT NULL,
  jurisdiction VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL
);
