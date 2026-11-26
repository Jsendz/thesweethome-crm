# CRM Phase 4 — Leads/Contacts (design)

## 1. Purpose and context

Phase 3 gave the CRM full property listing management. This phase adds the
first non-listing feature: tracking a lead from first contact through the
`deal` stage pipeline to a won close or a manually-marked cold lead, with
`activity` entries logging the calls/emails/meetings/viewings along the way.
It also closes out an item Phase 3 explicitly deferred — `property`'s link
to its owning `contact` — now that `contact` exists.

Source material: `docs/data-model.md` (v2, field tables for `contact`,
`consent_event`, `deal`, `activity`) and
`docs/superpowers/specs/2026-11-06-crm-foundation-design.md` (feature scope
in section 6.2, build phase ordering in section 7.4).

## 2. Decisions made during brainstorming

- **List/table pipeline view, not a Kanban board.** A stage dropdown on a
  deal (same pattern as the property edit form's status dropdown) is
  simpler to build and matches every other page in this app; a
  drag-between-columns board is a materially bigger feature for no present
  need at this agency's scale.
- **`consent_event` is out of scope for this phase.** The data model
  anticipates it, but it isn't part of Phase 4's named scope ("contact
  capture, deal stage pipeline, cold marking, activity logging") and
  deserves its own focused compliance pass rather than being bolted onto
  this one. No `consent_event` table is created this phase either — it's
  created when that pass actually needs it, not speculatively now.
- **No role restriction on contacts/deals/activities**, carrying forward
  Phase 3's ruling for the same reason: the agency currently has two users
  and no present need to separate who can touch a record. `owner_user_id`
  (contact) and `assigned_user_id` (deal) are informational/filtering
  fields this phase, not access-control gates.
- **There's no public lead-intake form to wire up.** The foundation spec's
  "replace the Sanity-backed website" work is explicitly later and
  separate (section 8 of the foundation spec). "Contact capture" in this
  phase means an agent manually logs a lead after a call, portal message,
  or walk-in — `contact.source`/`source_detail` record where it actually
  came from.
- **A deal is created from its contact's detail page, not a standalone
  `/deals/new`.** Every deal requires a `contact_id`, so the contact is
  the natural entry point; the `/deals` list page is for viewing and
  managing deals already in progress, not for starting new ones. Keeps a
  single, obvious place to start a deal instead of two forms that do the
  same thing.
- **Two deal-stage transitions are special-cased because they require a
  reason the plain stage dropdown doesn't collect**: moving to `COLD`
  requires `cold_reason` in the same request (and sets
  `cold_marked_by_user_id`/`cold_marked_at` to the acting user/now); moving
  to `CLOSED_LOST` requires `lost_reason`. Every other stage transition,
  including moving a deal back out of `COLD`, goes through the plain
  dropdown with no reason required and no enforced stage ordering —
  consistent with `PropertyController`'s precedent of required-field
  validation without a formal state machine.
- **Activities are logged against a deal, not a bare contact.** The data
  model allows `activity.deal_id`/`contact_id`/`property_id` to all be
  independently nullable, but scoping this phase to "log an activity on a
  deal" (inheriting `contact_id`/`property_id` from that deal) covers the
  named use case and avoids a second, parallel logging UI for
  not-yet-a-deal contacts. Easy to add later if it's actually needed.
- **Activities are create-only this phase** — no edit or delete, matching
  "logged interactions" as a running record rather than a mutable one. A
  mistaken entry gets corrected by logging a follow-up note, the same way
  you'd handle a misdial in a paper log.
- **`property.owner_contact_id` is added in this phase's migration**,
  closing out Phase 3 section 2's deferral. The property edit form gets a
  plain `<select>` of existing contacts (no type filtering — any contact
  can be picked as an owner, since the field is descriptive, not a
  constraint) and the existing generic `PATCH /api/properties/{id}`
  already accepts arbitrary field updates, so no new endpoint is needed.
- **Contact and deal pickers are plain `<select>` elements, not
  search-as-you-type.** At this agency's present contact volume a full
  list is easy to scan; revisit if the list grows large enough to need
  search.
- **A new minimal, non-ADMIN-gated endpoint backs every "pick a user"
  dropdown.** `UserController`'s `GET /api/users` is
  `@PreAuthorize("hasRole('ADMIN')")` and also returns `email`/`role`/
  `active` — too privileged and too detailed for the owner-agent/
  assigned-agent pickers this phase adds, which any logged-in user (not
  just ADMIN) needs to populate, per the no-role-gating decision above.
  Rather than loosen that endpoint's restriction, a new `GET
  /api/users/directory` returns just `{id, fullName}` for every active
  user, open to any authenticated user. `UserController`'s own ADMIN
  gating is unchanged.
- **`owner_user_id` is pickable, not just implicit.** `/contacts/new` and
  `/contacts/[id]/edit` both include an owner-agent `<select>` (populated
  from the directory endpoint above, defaulting to the acting user on
  create), alongside the `/contacts` list's existing "owner agent"
  column.

## 3. Data model / migrations

One new Flyway migration, `V4__create_contact_deal_activity_tables.sql`,
creating `contact`, `deal`, and `activity` per `docs/data-model.md`'s field
tables, plus the `property.owner_contact_id` column Phase 3 deferred.
`consent_event`, `offer`, and `document` are not created this phase (see
section 2 and section 8).

```sql
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
```

## 4. Deal stage transitions

On `PATCH /api/deals/{id}`, `stage` is editable like any other field
(`property_id`, `assigned_user_id`, `expected_close_date`), with two
exceptions that require a reason the plain PATCH body must also carry in
the same request:

- Setting `stage = COLD` without a non-blank `coldReason` in the same
  request returns 400. On success, `cold_marked_by_user_id` is set to the
  acting user and `cold_marked_at` to now, atomically with the stage
  change, and the audit row's changed-fields detail includes the reason.
- Setting `stage = CLOSED_LOST` without a non-blank `lostReason` in the
  same request returns 400.

No other transition is restricted — nothing stops moving a deal straight
from `LEAD` to `CLOSED_WON`, or back out of `COLD` into an active stage
(which simply leaves the earlier `cold_marked_*` fields in place as
history, since there's no need to clear them per anything in the data
model). This matches `PropertyController`'s existing precedent: required
fields are validated, but no formal state machine is enforced.

## 5. Backend components

- **`contact` package** — `Contact` JPA entity and repository.
  `ContactController`: `POST /api/contacts` (create; validates
  `firstName`/`lastName`/`contactType`/`source` required, defaults
  `ownerUserId` to the acting user if omitted), `GET /api/contacts` (list),
  `GET /api/contacts/{id}` (detail, including this contact's deals), `PATCH
  /api/contacts/{id}` (edit any field).
- **`deal` package** — `Deal` JPA entity and repository.
  `DealController`: `POST /api/deals` (create; validates `contactId`
  required and must reference an existing contact, 404 if not; defaults
  `stage = LEAD` and `assignedUserId` to the acting user if omitted), `GET
  /api/deals` (list; each row includes contact name, property reference if
  set, stage, assigned user, expected close date), `GET /api/deals/{id}`
  (detail), `PATCH /api/deals/{id}` (edit fields / change stage, special
  cases per section 4).
- **`activity` package** — `Activity` JPA entity and repository, nested
  under deals: `POST /api/deals/{id}/activities` (log a new activity
  against this deal; `contactId` and `propertyId` are inherited from the
  deal, not accepted in the request body; `userId` is always the acting
  user), `GET /api/deals/{id}/activities` (list, newest `occurredAt`
  first).
- **`UserController`**: a new `GET /api/users/directory` endpoint, no
  `@PreAuthorize` restriction (any authenticated user), returning
  `{id, fullName}` for every active `app_user` — backs the owner-agent/
  assigned-agent pickers below. The existing `GET /api/users` keeps its
  ADMIN-only gating and full field set unchanged.
- **`property.ownerContactId`**: added to `CreatePropertyRequest`,
  `UpdatePropertyRequest`, and `PropertyResponse` (optional, no validation
  beyond "must reference an existing contact if present") — reuses the
  existing `PropertyController.update()`/`create()` methods, no new
  endpoint.
- Every contact/deal create and update, and every activity create, calls
  `AuditLogService.log(...)` with `entityType` = `"CONTACT"` / `"DEAL"` /
  `"ACTIVITY"` respectively, matching the established pattern from
  `UserController`/`PropertyController`.

## 6. Frontend components

- **`/contacts`** — list view: table of contacts (name, type, source,
  phone/email, owner agent), a "New contact" button.
- **`/contacts/new`** — create form: all required `contact` fields, plus
  an owner-agent `<select>` (from `GET /api/users/directory`, defaulting
  to the acting user).
- **`/contacts/[id]`** — detail view: full field display, a list of this
  contact's deals (stage, property reference if any, assigned agent) each
  linking to `/deals/[id]`, a "New deal" button linking to
  `/contacts/[id]/deals/new`, an "Edit" link.
- **`/contacts/[id]/edit`** — edit form: same fields as create, including
  the owner-agent `<select>`.
- **`/contacts/[id]/deals/new`** — create-deal form scoped to this
  contact: optional property picker (`<select>` of existing properties by
  reference code), optional assigned-agent picker (defaults to self),
  optional expected close date.
- **`/deals`** — pipeline list view: table of all deals (contact name,
  property reference if any, stage, assigned agent, expected close date),
  sortable by stage, each row linking to `/deals/[id]`.
- **`/deals/[id]`** — detail view: full deal fields, a stage `<select>`
  (PATCHes on change) for ordinary transitions, two dedicated buttons —
  "Mark as cold" and "Close as lost" — that each prompt for their required
  reason before PATCHing, an "Edit" link for `propertyId`/
  `assignedUserId`/`expectedCloseDate`, and the activity log: a mini-form
  to log a new activity (type, notes, `occurredAt` defaulting to now,
  status) plus a chronological list of this deal's past activities.
- **`/deals/[id]/edit`** — edit form for `propertyId`/`assignedUserId`/
  `expectedCloseDate` (mirrors the property edit page's pattern, even
  though it's a shorter form, for consistency across the app).
- **`/properties/[id]/edit`** — gains an "Owner" field: a `<select>`
  populated from `GET /api/contacts`, alongside the existing fields.
- **`NavBar`** — add "Contacts" and "Deals" links, visible to everyone (no
  role gating, per section 2).

## 7. Testing

Backend (run on the user's machine, same pattern as Phases 1-3):

- Creating a contact with valid data succeeds, is retrievable, and writes
  a `CREATE` audit row.
- Creating a contact missing a required field (`firstName`, `lastName`,
  `contactType`, or `source`) returns 400.
- Editing a contact's fields succeeds and writes an `UPDATE` audit row.
- Creating a deal with valid data succeeds, defaults `stage = LEAD`, and
  writes a `CREATE` audit row.
- Creating a deal with a nonexistent `contactId` returns 404.
- Updating a deal's stage to an ordinary value (e.g. `QUALIFIED`) succeeds
  and writes an `UPDATE` audit row with the changed stage.
- Updating a deal's stage to `COLD` without `coldReason` returns 400; with
  it, succeeds, sets `coldMarkedByUserId`/`coldMarkedAt`, and writes an
  `UPDATE` audit row.
- Updating a deal's stage to `CLOSED_LOST` without `lostReason` returns
  400; with it, succeeds.
- Logging an activity against a deal succeeds, inherits `contactId`/
  `propertyId` from that deal, and writes a `CREATE` audit row with
  `entityType = "ACTIVITY"`.
- Listing a deal's activities returns them newest-first.
- Setting a property's `ownerContactId` via the existing `PATCH
  /api/properties/{id}` endpoint succeeds and is reflected in the
  property's response.
- `GET /api/users/directory` succeeds for a non-ADMIN authenticated user
  and returns only `id`/`fullName` (no `email`/`role`/`active`); the
  existing `GET /api/users` still rejects a non-ADMIN caller with 403,
  unchanged.

Frontend (self-verified in the cloud sandbox):

- Contacts list/create/detail/edit pages render and submit correctly.
- Deals list/detail/edit pages render correctly; the stage `<select>`
  change calls the update endpoint; "Mark as cold"/"Close as lost" each
  prompt for a reason and call the update endpoint with it.
- The activity mini-form on a deal's detail page submits and the new
  activity appears in the list.
- The property edit form's new "Owner" field renders options from fetched
  contacts and submits the selected id.

One manual full-stack pass: create a contact, create a deal for them
linked to an existing property, log a call and a scheduled viewing, move
the deal through a couple of stages, mark a different deal cold with a
reason, confirm the contacts/deals/activity list views all reflect it, and
set a property's owner to that contact.

## 8. Explicitly out of scope for this phase

- `consent_event` and any consent-logging UI — a dedicated compliance pass
  later (section 2).
- The `offer` entity and its status pipeline — Phase 5.
- The viewings calendar (a calendar view over `activity` where
  `activity_type = VIEWING`) — Phase 6; this phase only lets you log/view
  `VIEWING` activities as plain list rows, no calendar/date-grid
  rendering.
- A Kanban-style drag-and-drop pipeline board — list/dropdown only, per
  section 2.
- Role-based restrictions on who can see or edit contacts, deals, or
  activities.
- Logging an activity directly against a bare contact with no deal yet.
- Editing or deleting an activity once logged.
- The `document` entity (polymorphic document trail) — relevant once
  offers (Phase 5) need it.
- Multi-agency scoping enforcement (Postgres RLS) — same standing
  deferral as every prior phase.
- A formal state machine / stage-order enforcement for `deal.stage` beyond
  the two required-reason transitions in section 4.
- A searchable/autocomplete contact or property picker — a plain
  `<select>` is enough at this agency's present scale.
