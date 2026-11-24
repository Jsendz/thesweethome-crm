# CRM Phase 3 — Property Listings End-to-End (design)

## 1. Purpose and context

Phase 2 gave the CRM individual logins, roles, and an audit trail. This
phase adds the first real feature: full management of property listings,
including the bulk multi-image upload pipeline that every later phase's
listing references (leads, offers, the dashboard) will build on top of.

Source material: `docs/data-model.md` (v2, field tables for `property`,
`property_translation`, `property_media`) and
`docs/superpowers/specs/2026-11-06-crm-foundation-design.md` (overall
architecture, image pipeline cross-cutting requirement in section 5, build
phase ordering in section 7).

## 2. Decisions made during brainstorming

- **No role restriction on listings, for now.** Any logged-in user (ADMIN,
  MANAGER, or AGENT) can create, edit, and withdraw a property. The agency
  currently has two users and no present need to separate who can touch
  listings. Revisit once there are enough users for it to matter — nothing
  about the schema or endpoints below blocks adding `@PreAuthorize` checks
  later.
- **Image processing is synchronous, at upload time.** The bulk-upload
  request processes every photo inline — strip EXIF/GPS, auto-orient,
  resize to the four sizes, encode, upload to MinIO, insert one
  `property_media` row per photo with `variants` already filled in —
  before the response returns. No background job, no per-photo
  "processing" state to track in the UI. This matches the foundation
  spec's own wording ("processed once, server-side, at upload time") and
  is simpler to build; nothing here blocks moving to a background job
  later if upload batches ever get large enough to need one.
- **WebP encoding is a best-effort enhancement, not a hard requirement.**
  Java has no built-in WebP writer. `org.sejda.imageio:webp-imageio` is the
  one actively-usable library found that can encode (not just decode) WebP
  from Java, and it bundles native code for Linux; whether it also works
  on the user's Windows dev machine is unverified. Task 1 of the
  implementation plan is a small spike — encode a sample image, read it
  back — run before anything else. If it doesn't work on Windows, every
  size falls back to JPEG-only; the resize/variants pipeline, the schema,
  and the upload flow don't change either way.
- **A new listing requires a translation in exactly one locale at
  creation: Catalan (`ca`)** — the agency's own `default_locale` (seeded
  as `'ca'` in `V1__create_agency_app_user_audit_log.sql`). Spanish,
  French, and English translations can be added afterward via edit. This
  avoids forcing a four-language form on every listing up front.
- **This phase's bulk upload only handles `PHOTO` and `FLOORPLAN`.** Both
  are static images and go through the same resize/encode pipeline.
  `VIDEO` and `VIRTUAL_TOUR` stay in the `media_type` enum for later (they
  need entirely different handling — transcoding or just storing an
  embed link — and aren't part of this phase's scope).
- **Object storage via the AWS S3 SDK, pointed at MinIO's S3-compatible
  endpoint**, rather than MinIO's own client library. Functionally
  equivalent for this project; the AWS SDK is the more broadly useful
  thing to have learned, and the same code points at real S3 unchanged if
  that's ever relevant.
- **`property.owner_contact_id` is deferred to Phase 4.** The data model
  gives `property` a nullable FK to `contact`, but `contact` doesn't exist
  until Phase 4 (Leads/contacts). This phase's migration creates
  `property` without that column; Phase 4's migration adds it once
  `contact` exists. The create/edit form has no "owner" field this phase
  for the same reason — there's nothing yet to pick from.
- **`currency` is fixed to `EUR`** for this phase (Andorra's currency; the
  field exists in the schema for future flexibility but there's no
  present need for a currency picker in the form).
- **`reference_code` is agent-entered, not auto-generated**, and unique
  per agency (agencies commonly have their own listing-numbering
  convention) — enforced the same way Phase 2 enforced unique email: a DB
  unique index, with the create endpoint converting a violation into a
  clean 409.
- **"Withdraw" sets `status = WITHDRAWN`** via the same update endpoint
  used for any other field edit — there's no separate delete endpoint,
  consistent with the data model's disable-not-delete rule already
  established for `app_user`.
- **No real multi-agency scoping**, consistent with the same deferred
  decision already made in Phase 2 for `UserController`: every property
  is created under the one seeded agency, and reads aren't filtered by
  agency. Not a new gap — the same one, extended.
- **Creating a property and its mandatory `ca` translation is one atomic
  request.** The create payload carries `caTitle`/`caDescription`
  directly, inserted in the same transaction as the `property` row — so
  there's never a moment where a property exists with no translation. The
  `POST /api/properties/{id}/translations` endpoint (section 5) is only
  for *adding* `es`/`fr`/`en` afterward; editing the `ca` title/description
  later goes through `PATCH /api/properties/{id}` alongside every other
  field, not through `/translations`.
- **`property_media.variants` stores direct, public URLs, not presigned
  ones** — this is what the foundation spec's section 5 already says
  ("the resulting URLs written into `property_media.variants`"), which
  only makes sense for a permanent, non-expiring URL. The MinIO bucket is
  created public-read at startup (alongside the idempotent
  bucket-creation step in section 4), and the frontend's `<picture>`/
  `srcset` markup points straight at them.
- **A bulk-upload failure cleans up after itself.** Since each file is
  uploaded to MinIO before its `property_media` row is inserted (section
  4), a failure partway through a batch could otherwise leave orphaned
  objects in MinIO with nothing to find or remove them. On any failure,
  `PropertyController` deletes every object this request already
  uploaded before returning the error — no orphaned blobs, no cleanup job
  needed.

## 3. Data model / migrations

One new Flyway migration, `V3__create_property_tables.sql`, creating
`property`, `property_translation`, and `property_media` per
`docs/data-model.md`'s field tables, with these adjustments from section 2
above: no `owner_contact_id` column yet, `currency` has a `DEFAULT 'EUR'`
with no application-level picker, and a unique index on
`(agency_id, reference_code)`.

```sql
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
```

## 4. Image pipeline

On `POST /api/properties/{id}/media` (multipart, one or more files):

1. For each file, in order: read it with Thumbnailator (handles
   orientation from EXIF automatically), strip EXIF/GPS metadata, produce
   four resized copies — thumbnail (~400px), card (~800px), gallery
   (~1600px), full-detail (capped ~3000px) — each on the longest edge,
   preserving aspect ratio.
2. Encode each of the four sizes as JPEG (always) and WebP (if the
   Task-1 spike confirms it works on the target platform).
3. Upload each resulting file to MinIO under a path like
   `properties/{propertyId}/{mediaId}/{size}.{format}`.
4. Insert one `property_media` row with `media_type` (`PHOTO` or
   `FLOORPLAN`, from the request), `variants` as a JSON map of
   `{ "thumbnail_webp": "...", "thumbnail_jpeg": "...", "card_webp": ..., ... }`
   (omitting the `_webp` keys if WebP isn't available), and `sort_order`
   set to the next value after the property's existing media.
5. If any file in the batch fails to process, the whole request fails
   (no partial inserts) and reports which file and why — bulk upload is
   one request, one outcome. Any object already uploaded to MinIO earlier
   in this same request is deleted as part of handling the failure, so a
   failed batch leaves nothing behind.

MinIO is added to `docker-compose.yml` as a new service (same pattern as
`postgres`), with a bucket the backend creates on startup if missing — the
same idempotent-seeding pattern `AdminUserSeeder` already uses for the
admin account.

## 5. Backend components

- **`property` package** — `Property`, `PropertyTranslation`,
  `PropertyMedia` JPA entities and their repositories.
- **`PropertyController`** — `POST /api/properties` (create; payload
  includes `caTitle`/`caDescription` alongside the property fields and
  inserts the property row plus its `ca` translation in one transaction,
  per section 2; validates required fields, 409 on duplicate
  `reference_code`), `GET /api/properties` (list), `GET
  /api/properties/{id}` (detail, including translations and media), `PATCH
  /api/properties/{id}` (edit any field, including `caTitle`/
  `caDescription` and `status`; this is how "withdraw" happens and how the
  Catalan translation gets edited after creation), `POST
  /api/properties/{id}/translations` (add or update only a non-`ca` locale
  — `es`/`fr`/`en`), `POST /api/properties/{id}/media` (bulk upload, see
  section 4), `PATCH /api/properties/{id}/media/order` (reorder via
  `sort_order`), `DELETE /api/properties/{id}/media/{mediaId}` (remove one
  photo — the one delete this phase has, since a wrongly-uploaded photo
  isn't audit-sensitive the way a user or listing record is; also deletes
  the underlying MinIO objects).
- **`ImagePipelineService`** — the logic in section 4, steps 1-2.
- **`ObjectStorageService`** — thin wrapper around an injected
  `software.amazon.awssdk.services.s3.S3Client` configured to point at
  MinIO's endpoint; upload/delete-by-key methods.
- Every create/update/media-upload/media-delete calls
  `AuditLogService.log(...)` with `entityType = "PROPERTY"`, the same
  pattern Phase 2 established for `app_user`.
- New dependencies: `software.amazon.awssdk:s3`, `net.coobird:thumbnailator`,
  `org.sejda.imageio:webp-imageio` (conditionally used per the Task-1
  spike result).

## 6. Frontend components

- **`/properties`** — list view: a table of existing properties
  (reference code, type, operation, status, price, a small thumbnail from
  the first photo), a "New listing" button, visible to any logged-in user
  (no role gating, per section 2).
- **`/properties/new`** — create form: all required `property` fields from
  section 3, plus a required Catalan title/description.
- **`/properties/[id]`** — detail view: full field display, the photo
  gallery (using the `card` or `gallery` variant), an "Edit" link, and a
  translations section for adding `es`/`fr`/`en` later.
- **`/properties/[id]/edit`** — edit form: same fields as create, plus the
  `status` dropdown (this is where "withdraw" happens — selecting
  `WITHDRAWN` and saving), and the image management widget: multi-file
  picker for bulk upload, a thumbnail grid with drag-to-reorder (writes
  `sort_order`) and a delete button per photo.
- **`NavBar`** — add a "Listings" link, visible to everyone (consistent
  with no role gating this phase).

## 7. Testing

Backend (run on the user's machine, same pattern as Phases 1-2):

- Creating a property with valid data succeeds, is retrievable, and
  writes a `CREATE` audit row.
- Creating a property with a duplicate `reference_code` (same agency)
  returns 409.
- Creating a property without a `ca` translation returns 400.
- Editing fields, including setting `status = WITHDRAWN`, succeeds and
  writes an `UPDATE` audit row with the changed fields.
- Uploading a batch of real small test images produces the expected
  number of `property_media` rows, each with the expected set of variant
  keys and files actually present in MinIO (run against the local MinIO
  from `docker-compose.yml`, the same way backend tests already run
  against the local Postgres rather than Testcontainers) — each test
  cleans up the objects it writes.
- `AdminUserSeeder`-style bucket creation is idempotent (safe to run with
  the bucket already present).

Frontend (self-verified in the cloud sandbox):

- List page renders properties and the "New listing" link.
- Create form submits all required fields and shows validation errors for
  missing ones.
- Edit form loads existing values, submits changes, and the status
  dropdown includes `WITHDRAWN`.
- Image upload widget renders a preview grid and calls the upload
  endpoint with the selected files.

One manual full-stack pass: create a listing with a few real photos,
confirm the sizes render correctly in the list and detail views, edit a
field, withdraw the listing, confirm the status reflects it.

## 8. Explicitly out of scope for this phase

- `owner_contact_id` and any UI for linking a property to a contact —
  blocked on Phase 4's `contact` table (see section 2).
- Any public-facing listings page — this phase is admin-only; the
  separate project replacing the marketing site's Sanity CMS integration
  is still future work per the foundation spec.
- `VIDEO` and `VIRTUAL_TOUR` media types.
- Role-based restrictions on who can manage listings.
- Multi-currency support, multi-agency scoping enforcement (Postgres RLS).
- Map display for `latitude`/`longitude` (Mapbox/MapTiler integration is
  listed in the stack but not tied to a specific phase yet).
