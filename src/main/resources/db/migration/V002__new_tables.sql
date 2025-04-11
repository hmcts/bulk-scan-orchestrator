--- Create payments table
CREATE TABLE IF NOT EXISTS payment (
  id UUID NOT NULL PRIMARY KEY,
  envelope_id character varying(50) NOT NULL,
  ccd_reference character varying(50) NOT NULL,
  is_exception_record BOOLEAN NOT NULL,
  po_box character varying(20) NOT NULL,
  jurisdiction character varying(50) NOT NULL,
  service character varying(50) NOT NULL,
  payments TEXT[],
  status character varying(20) NOT NULL,
  status_message character varying(255),
  created_at timestamp NOT NULL,
  last_updated_at timestamp NOT NULL
);

-- Update payments table
CREATE TABLE IF NOT EXISTS update_payment (
  id UUID NOT NULL PRIMARY KEY,
  envelope_id character varying(50) NOT NULL,
  jurisdiction character varying(50) NOT NULL,
  exception_record_ref character varying(50) NOT NULL,
  new_case_ref character varying(50) NOT NULL,
  status character varying(20) NOT NULL,
  status_message character varying(255),
  created_at timestamp NOT NULL,
  last_updated_at timestamp NOT NULL
)