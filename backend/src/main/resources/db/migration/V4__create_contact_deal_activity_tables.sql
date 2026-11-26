CREATE TABLE contact (
    id uuid PRIMARY KEY,
    agency_id uuid NOT NULL REFERENCES agency(id),
    contact_type text NOT NULL CHECK (contact_type IN
        ('BUYER','SELLER','TENANT','LANDLORD','LEAD')),
    first_name text NOT NULL,
    last_name text NOT NULL,
    email text,
    phone text,
    preferred_locale text,
    source text NOT NULL CHECK (source IN
        ('PORTAL','WEBSITE','REFERRAL','MANUAL','WALK_IN')),
    source_detail text,
    notes text,
    owner_user_id uuid NOT NULL REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX contact_agency_id_idx ON contact (agency_id);

CREATE TABLE deal (
    id uuid PRIMARY KEY,
    agency_id uuid NOT NULL REFERENCES agency(id),
    contact_id uuid NOT NULL REFERENCES contact(id),
    property_id uuid REFERENCES property(id),
    assigned_user_id uuid NOT NULL REFERENCES app_user(id),
    stage text NOT NULL DEFAULT 'LEAD' CHECK (stage IN
        ('LEAD','QUALIFIED','VIEWING_SCHEDULED','VIEWED','OFFER_MADE',
         'NEGOTIATION','UNDER_CONTRACT','CLOSED_WON','CLOSED_LOST','COLD')),
    lost_reason text,
    cold_reason text,
    cold_marked_by_user_id uuid REFERENCES app_user(id),
    cold_marked_at timestamptz,
    expected_close_date date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX deal_agency_id_idx ON deal (agency_id);
CREATE INDEX deal_contact_id_idx ON deal (contact_id);

CREATE TABLE activity (
    id uuid PRIMARY KEY,
    agency_id uuid NOT NULL REFERENCES agency(id),
    contact_id uuid REFERENCES contact(id),
    deal_id uuid REFERENCES deal(id),
    property_id uuid REFERENCES property(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    activity_type text NOT NULL CHECK (activity_type IN
        ('CALL','EMAIL','VIEWING','MEETING','NOTE','WHATSAPP')),
    status text NOT NULL DEFAULT 'COMPLETED' CHECK (status IN
        ('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW')),
    notes text,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX activity_deal_id_idx ON activity (deal_id);

ALTER TABLE property ADD COLUMN owner_contact_id uuid REFERENCES contact(id);
