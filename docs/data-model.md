# CRM Data Model — v2

Real estate CRM for an Andorran agency, modeled on Spanish real estate CRM conventions.
Target stack: PostgreSQL (self-hosted or Supabase), accessed via Spring Data JPA/Hibernate
from a Java/Spring Boot backend. Built single-agency first, with an `agency_id` column kept
on every table so it can become multi-tenant later without a schema rewrite.

v2 adds the four workflows confirmed in this round: full listing management with bulk image
upload, lead tracking from first contact to success/cold, offers tracked through to notary,
and a viewings calendar. Decisions locked in: the offer-to-notary pipeline below, one active
offer per deal (no multi-offer history for now), leads go cold by manual agent action only,
and the calendar is in-app only (no Google/Outlook sync).

Two standing design choices carried over from v1, both driven by the Andorran data protection
law (LQPD/RLQPD, enforced by the APDA):

1. **Consent is an append-only log (`consent_event`), not a boolean** — proves when and how
   consent was given.
2. **Every sensitive action gets an `audit_log` row** — backbone of the agency's Record of
   Processing Activities.

---

## Entities

### `agency` (tenant)
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| name | text | trading name |
| legal_name | text | |
| tax_id | text | Andorran NRT / Spanish NIF |
| country | text | ISO code |
| default_locale | text | ca, es, fr, en |
| dpo_name / dpo_email | text, nullable | data protection officer, if designated |
| subscription_plan / subscription_status | text | unused for now, kept for later |
| created_at | timestamptz | |

### `app_user`
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| email | text, unique | |
| password_hash | text | |
| full_name | text | |
| role | enum | ADMIN, MANAGER, AGENT |
| phone | text, nullable | |
| preferred_locale | text | |
| active | boolean | disable instead of delete, for audit continuity |
| last_login_at | timestamptz, nullable | |
| created_at | timestamptz | |

### `contact`
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| contact_type | enum | BUYER, SELLER, TENANT, LANDLORD, LEAD |
| first_name / last_name | text | |
| email / phone | text, nullable | |
| preferred_locale | text | |
| source | enum | PORTAL, WEBSITE, REFERRAL, MANUAL, WALK_IN |
| source_detail | text, nullable | e.g. which portal, or "property request — ref X" |
| notes | text, nullable | |
| owner_user_id | uuid, FK → app_user | agent responsible |
| created_at / updated_at | timestamptz | |

Consent is **not** a field here — see `consent_event`.

### `consent_event` (append-only)
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| contact_id | uuid, FK | |
| consent_type | enum | DATA_PROCESSING, MARKETING_COMMS, THIRD_PARTY_SHARING |
| granted | boolean | true = given, false = withdrawn |
| method | enum | FORM, EMAIL, VERBAL_LOGGED, SIGNED_DOCUMENT |
| recorded_by_user_id | uuid, FK | |
| occurred_at | timestamptz | |
| evidence_ref | text, nullable | link to stored form/document |

### `property`
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| reference_code | text | agency's own listing code |
| property_type | enum | APARTMENT, HOUSE, LAND, COMMERCIAL, PARKING, OTHER |
| operation | enum | SALE, RENT, SALE_OR_RENT |
| status | enum | DRAFT, ACTIVE, RESERVED, UNDER_OFFER, SOLD, RENTED, WITHDRAWN |
| owner_contact_id | uuid, FK → contact, nullable | the seller/landlord |
| address_line / parish / country | text | parish = Andorra's 7 parishes, or municipality if the product expands to Spain |
| latitude / longitude | numeric, nullable | |
| price | numeric | |
| currency | text | default EUR |
| area_m2 / rooms / bathrooms / year_built / energy_rating | — | full listing detail fields |
| is_exclusive / exclusivity_end_date | boolean / date | |
| created_by_user_id | uuid, FK | |
| created_at / updated_at | timestamptz | |

### `property_translation`
Multi-language title/description per locale (ca, es, fr, en) — `(property_id, locale)` unique.

### `property_media`
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| property_id | uuid, FK | |
| media_type | enum | PHOTO, FLOORPLAN, VIDEO, VIRTUAL_TOUR |
| variants | jsonb | map of size/format → URL (thumbnail/card/gallery/full-detail, WebP+JPEG) — produced by the upload-time image pipeline, not a single `url` field |
| sort_order | int | supports reordering after a bulk upload |
| uploaded_at | timestamptz | |

Bulk upload is a batch of `property_media` inserts against one `property_id` in a single
request — the backend processes each file through the same resize/WebP pipeline and inserts
one row per source photo. No schema change needed beyond the `variants` field already in v1.

### `deal`
Tracks a lead's progress — from first contact through to a close, or going cold.

| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| contact_id | uuid, FK | the lead/client side |
| property_id | uuid, FK, nullable | the property that triggered the request, once known |
| assigned_user_id | uuid, FK → app_user | |
| stage | enum | LEAD, QUALIFIED, VIEWING_SCHEDULED, VIEWED, OFFER_MADE, NEGOTIATION, UNDER_CONTRACT, CLOSED_WON, CLOSED_LOST, **COLD** |
| lost_reason | text, nullable | why CLOSED_LOST (got far, then fell through) |
| cold_reason | text, nullable | why COLD (went quiet early, agent gave up) |
| cold_marked_by_user_id / cold_marked_at | uuid / timestamptz, nullable | manual-only, per this round's decision |
| expected_close_date | date, nullable | |
| created_at / updated_at | timestamptz | |

**COLD vs. CLOSED_LOST**, as agreed: COLD is an early-stage lead an agent manually gives up
on (no offer ever got that far); CLOSED_LOST is a deal that progressed — typically through an
`offer` — and still fell through. Both are manually set; nothing here auto-flags a lead cold.

### `offer`
One row per deal — the *current* offer, not a full history (per this round's decision: "one
active offer at a time"). If an offer is replaced (rejected, then resubmitted higher), this
row's fields are updated in place rather than a new row being inserted. `deal_id` is unique,
so a deal has at most one offer at any time.

| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| deal_id | uuid, FK, **unique** | one current offer per deal |
| amount | numeric | |
| currency | text | default EUR |
| status | enum | SUBMITTED, COUNTERED, ACCEPTED, UNDER_CONDITIONS, NOTARY_SCHEDULED, COMPLETED, REJECTED, WITHDRAWN |
| submitted_date | date | |
| notary_date | date, nullable | set once NOTARY_SCHEDULED |
| conditions | text, nullable | financing contingency, etc. |
| notes | text, nullable | |
| created_by_user_id | uuid, FK | |
| created_at / updated_at | timestamptz | |

Pipeline, as confirmed: **Submitted → Countered → Accepted → Under Conditions → Notary
Scheduled → Completed**, with **Rejected** or **Withdrawn** as exits at any point before
Accepted. If you later want full offer history (every rejected/resubmitted offer kept, not
just the current one), drop the `unique` constraint on `deal_id` — nothing else changes.

### `activity`
Logged interactions, and the data source for the viewings calendar.

| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| contact_id / deal_id / property_id | uuid, FK, nullable | |
| user_id | uuid, FK → app_user | who logged it / who it's assigned to |
| activity_type | enum | CALL, EMAIL, VIEWING, MEETING, NOTE, WHATSAPP |
| status | enum | SCHEDULED, COMPLETED, CANCELLED, NO_SHOW — mainly meaningful for VIEWING; other types default COMPLETED since they're logged after the fact |
| notes | text, nullable | |
| occurred_at | timestamptz | scheduled time for a future viewing, actual time for a logged one |
| created_at | timestamptz | |

The calendar view is just `activity` filtered to `activity_type = VIEWING`, grouped by date —
no separate table needed, and no external sync per this round's decision.

### `document`
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| related_entity_type | enum | CONTACT, PROPERTY, DEAL, OFFER |
| related_entity_id | uuid | polymorphic reference |
| name | text | |
| file_url | text | pointer to object storage |
| signature_status | enum | NONE, PENDING, SIGNED, DECLINED |
| signature_provider / signature_provider_ref | text, nullable | e.g. "signaturit" + external doc id |
| uploaded_by_user_id | uuid, FK | |
| created_at | timestamptz | |

### `audit_log` (append-only)
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| actor_user_id | uuid, FK | |
| action | enum | VIEW, CREATE, UPDATE, DELETE, EXPORT |
| entity_type / entity_id | text / uuid | |
| detail | jsonb, nullable | e.g. changed fields |
| ip_address | text, nullable | |
| occurred_at | timestamptz | |

### `portal_feed_config`
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| portal_name | text | |
| integration_type | enum | XML_EXPORT, EMAIL_LEAD_PARSE, API |
| config | jsonb | credentials/endpoint, feed format specifics |
| active | boolean | |
| last_synced_at | timestamptz, nullable | |

### `retention_policy`
| field | type | notes |
|---|---|---|
| id | uuid, PK | |
| agency_id | uuid, FK | |
| entity_type | text | |
| retention_period_days | int | |
| last_run_at | timestamptz, nullable | |

---

## Relationships at a glance

```
agency 1──N app_user, contact, property, deal, document, audit_log, portal_feed_config, retention_policy

contact 1──N consent_event, deal, activity
contact N──1 app_user (owner_user_id)

property 1──N property_translation, property_media
property N──1 contact (owner_contact_id, nullable)
property 1──N deal (nullable FK on deal side)

deal N──1 contact
deal N──1 property (nullable)
deal N──1 app_user (assigned_user_id)
deal 1──N activity
deal 1──0/1 offer   ← new in v2

document → polymorphic (CONTACT | PROPERTY | DEAL | OFFER) via related_entity_type/id
activity → optional FKs to contact / deal / property; VIEWING rows drive the calendar
```

---

## Draft SQL (PostgreSQL) — new/changed tables since v1

The v1 tables (`agency`, `app_user`, `contact`, `property`, `property_translation`,
`property_media`, `audit_log`, `portal_feed_config`, `retention_policy`) are unchanged except
where noted. Full DDL for those is in the earlier draft; below is what v2 adds or alters.

```sql
-- deal: add COLD stage + cold-tracking fields
alter table deal
  drop constraint if exists deal_stage_check;
alter table deal
  add constraint deal_stage_check check (stage in
    ('LEAD','QUALIFIED','VIEWING_SCHEDULED','VIEWED','OFFER_MADE','NEGOTIATION',
     'UNDER_CONTRACT','CLOSED_WON','CLOSED_LOST','COLD'));
alter table deal add column if not exists cold_reason text;
alter table deal add column if not exists cold_marked_by_user_id uuid references app_user(id);
alter table deal add column if not exists cold_marked_at timestamptz;

-- activity: add status for the calendar (scheduled vs. completed/cancelled viewings)
alter table activity add column if not exists status text not null default 'COMPLETED'
  check (status in ('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW'));

-- offer: new table, one row per deal (current offer only)
create table offer (
  id uuid primary key default gen_random_uuid(),
  agency_id uuid not null references agency(id),
  deal_id uuid not null unique references deal(id),
  amount numeric(14,2) not null,
  currency text not null default 'EUR',
  status text not null default 'SUBMITTED' check (status in
    ('SUBMITTED','COUNTERED','ACCEPTED','UNDER_CONDITIONS','NOTARY_SCHEDULED',
     'COMPLETED','REJECTED','WITHDRAWN')),
  submitted_date date not null default current_date,
  notary_date date,
  conditions text,
  notes text,
  created_by_user_id uuid references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on offer (agency_id);
create index on offer (deal_id);

-- document: extend polymorphic type to include OFFER
alter table document drop constraint if exists document_related_entity_type_check;
alter table document add constraint document_related_entity_type_check
  check (related_entity_type in ('CONTACT','PROPERTY','DEAL','OFFER'));
```

---

## Open items for next round

- **Offer document trail**: the reserva/compromís/escriptura documents tied to an offer go
  through `document` with `related_entity_type = 'OFFER'` — confirm that's the right place
  rather than a dedicated `offer_document` table once you see how many documents a typical
  offer actually accumulates.
- **Viewing conflicts**: the calendar reads `activity` directly; if double-booking an agent
  becomes a real problem, that's an application-layer check (query overlapping `occurred_at`
  ranges for the same `user_id`), not a schema change.
- **Commission tracking** and **rental-specific fields** (recurring rent, deposit, lease term)
  are still open from v1 — not addressed this round.
