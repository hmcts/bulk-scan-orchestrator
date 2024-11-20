CREATE TABLE payments_table (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  envelope_id VARCHAR(50) NOT NULL,
  ccd_reference VARCHAR(50) NOT NULL,
  jurisdiction VARCHAR(50) NOT NULL,
  service VARCHAR(50) NOT NULL,
  po_box VARCHAR(50),
  is_exception_record BOOLEAN NOT NULL,
  status VARCHAR(50) NOT NULL
  );

CREATE TABLE payments_data (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  payment_id UUID REFERENCES payments_table(id),
  document_control_number VARCHAR(50) NOT NULL
);
