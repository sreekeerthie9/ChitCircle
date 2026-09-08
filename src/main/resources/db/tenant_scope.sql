CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS tenant_id uuid,
  ADD COLUMN IF NOT EXISTS parent_id integer;

UPDATE users
SET tenant_id = gen_random_uuid()
WHERE tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_tenant_id
  ON users (tenant_id);

CREATE INDEX IF NOT EXISTS idx_users_parent_id
  ON users (parent_id);

CREATE INDEX IF NOT EXISTS idx_users_tenant_parent
  ON users (tenant_id, parent_id);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_users_parent'
  ) THEN
    ALTER TABLE users
      ADD CONSTRAINT fk_users_parent
      FOREIGN KEY (parent_id) REFERENCES users (id);
  END IF;
END $$;

ALTER TABLE users
  ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE chit_groups
  ADD COLUMN IF NOT EXISTS name varchar(255);

UPDATE chit_groups
SET name = CONCAT('Group #', id)
WHERE name IS NULL OR name = '';

ALTER TABLE chit_groups
  ALTER COLUMN name SET NOT NULL;

ALTER TYPE group_status
  ADD VALUE IF NOT EXISTS 'BIDDING_OPEN';

CREATE TABLE IF NOT EXISTS chit_scheme_schedules (
  id bigserial PRIMARY KEY,
  scheme_id bigint NOT NULL REFERENCES chit_schemes(id) ON DELETE CASCADE,
  month_number integer NOT NULL CHECK (month_number > 0),
  month_name varchar(20) NOT NULL,
  year_number integer NOT NULL CHECK (year_number >= 0),
  pit_amount numeric(14, 2) NOT NULL CHECK (pit_amount >= 0),
  member_payment numeric(14, 2) NOT NULL CHECK (member_payment >= 0),
  CONSTRAINT uk_scheme_schedule_month UNIQUE (scheme_id, month_number)
);

CREATE INDEX IF NOT EXISTS idx_scheme_schedules_scheme_id
  ON chit_scheme_schedules (scheme_id);

CREATE TABLE IF NOT EXISTS payouts (
  id bigserial PRIMARY KEY,
  cycle_id bigint NOT NULL UNIQUE REFERENCES cycles(id),
  membership_id bigint NOT NULL REFERENCES memberships(id),
  amount numeric(14, 2) NOT NULL CHECK (amount >= 0),
  status payment_status NOT NULL DEFAULT 'PAID',
  method varchar(50) NOT NULL,
  note text,
  receipt_object_key varchar(1024),
  receipt_file_name varchar(255),
  receipt_content_type varchar(100),
  paid_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payouts_membership_id ON payouts (membership_id);
CREATE INDEX IF NOT EXISTS idx_payouts_paid_at ON payouts (paid_at DESC);
