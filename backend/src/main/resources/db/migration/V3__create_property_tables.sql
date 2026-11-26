CREATE TABLE property (
    id uuid PRIMARY KEY,
    agency_id uuid NOT NULL REFERENCES agency(id),
    reference_code text NOT NULL,
    property_type text NOT NULL CHECK (property_type IN
        ('APARTMENT','HOUSE','LAND','COMMERCIAL','PARKING','OTHER')),
    operation text NOT NULL CHECK (operation IN ('SALE','RENT','SALE_OR_RENT')),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN
        ('DRAFT','ACTIVE','RESERVED','UNDER_OFFER','SOLD','RENTED','WITHDRAWN')),
    address_line text,
    parish text,
    country text NOT NULL DEFAULT 'AD',
    latitude numeric,
    longitude numeric,
    price numeric(14,2) NOT NULL,
    currency text NOT NULL DEFAULT 'EUR',
    area_m2 numeric,
    rooms integer,
    bathrooms integer,
    year_built integer,
    energy_rating text,
    is_exclusive boolean NOT NULL DEFAULT false,
    exclusivity_end_date date,
    created_by_user_id uuid REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX property_agency_reference_code_idx
    ON property (agency_id, reference_code);

CREATE TABLE property_translation (
    id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES property(id),
    locale text NOT NULL CHECK (locale IN ('ca','es','fr','en')),
    title text NOT NULL,
    description text,
    UNIQUE (property_id, locale)
);

CREATE TABLE property_media (
    id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES property(id),
    media_type text NOT NULL CHECK (media_type IN
        ('PHOTO','FLOORPLAN','VIDEO','VIRTUAL_TOUR')),
    variants jsonb NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    uploaded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX property_media_property_id_idx ON property_media (property_id);
