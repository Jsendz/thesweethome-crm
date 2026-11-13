CREATE TABLE agency (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    legal_name text,
    tax_id text,
    country text,
    default_locale text,
    dpo_name text,
    dpo_email text,
    subscription_plan text,
    subscription_status text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    agency_id uuid NOT NULL REFERENCES agency(id),
    email text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    full_name text NOT NULL,
    role text NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'AGENT')),
    phone text,
    preferred_locale text,
    active boolean NOT NULL DEFAULT true,
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id uuid PRIMARY KEY,
    agency_id uuid NOT NULL REFERENCES agency(id),
    actor_user_id uuid REFERENCES app_user(id),
    action text NOT NULL CHECK (action IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'EXPORT', 'LOGIN', 'LOGIN_FAILED')),
    entity_type text,
    entity_id uuid,
    detail jsonb,
    ip_address text,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO agency (id, name, legal_name, country, default_locale, subscription_plan, subscription_status)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'The Sweet Home Company',
    'The Sweet Home Company',
    'AD',
    'ca',
    'unused',
    'unused'
);
