# Real Estate CRM — Foundation Design

Status: approved by user on 2026-11-06, ready for implementation planning.

## 1. Purpose and context

The user runs a real estate agency in Andorra and wants a CRM modeled on Spanish
real estate CRM conventions, built first for their own agency's internal use
(not yet a multi-agency product, though the schema is kept tenant-ready via an
`agency_id` column on every table so it can become one later without a rewrite).

The user also wants to use this project to become more proficient in Java;
Spring Boot was evaluated on its own merits (not just as a learning vehicle)
and judged genuinely well-suited to a RBAC/audit/compliance-heavy transactional
business application, so the two goals don't trade off against each other.

Constraints that shaped every decision below:
- **Budget**: free/self-hosted is strongly preferred over paid managed
  services, even where that means more manual ops work. Mapbox/MapTiler's
  free tier is the one accepted exception (self-hosting map tiles was judged
  not worth the effort at this scale).
- **Performance**: property images must load quickly and display in high
  detail — this drove the upload-time image processing pipeline (section 5).
- **Compliance**: Andorra's data protection law (LQPD 2021 / RLQPD 2022,
  enforced by the APDA) expects data minimization, explicit/separable
  consent, individual non-shared logins, role-based access, and an audit
  trail — these are built into the schema from the start (`consent_event`,
  `audit_log`), not retrofitted.
- **No payments**: real estate transactions in Andorra aren't paid through
  the website, and agency billing isn't needed yet since this isn't being
  sold to other agencies at this stage. No payment integration is in scope.

A later, separate project (out of scope for this spec — see section 8) will
replace the user's existing Next.js website's Sanity CMS integration with
this CRM as the data source for property listings.

## 2. Success criteria

The CRM is working when the user, as the sole agent/admin of their own
agency, can: create and manage property listings including bulk multi-image
upload; track a lead from first contact through to a won deal or a cold
lead; submit and track an offer on a property through to notary signing;
and see a calendar of arranged viewings — all behind individual logins, with
an audit trail, running on infrastructure that costs nothing beyond the
Mapbox/MapTiler free tier.

## 3. Stack (final)

Single Oracle Cloud "Always Free" VM (EU region, ideally Frankfurt),
running Docker Compose with:

- **PostgreSQL**, self-hosted, as the system of record.
- **MinIO**, self-hosted, S3-compatible object storage for property images
  and documents.
- **Spring Boot** (Java) backend: Spring Data JPA/Hibernate, Spring
  Security, Spring's auditing support, Quartz for scheduled jobs (e.g.
  retention-policy sweeps, future portal feed sync).
- **Next.js + TypeScript** admin frontend (the CRM UI itself — separate
  from the user's existing marketing/listings website), with `next-intl`
  for ca/es/fr/en.
- **Caddy** as reverse proxy, for free automatic TLS via Let's Encrypt.
- **Documenso** (self-hosted, AGPL-3.0) for e-signature, once the
  offer/notary document workflow needs it.
- **Uptime Kuma** for uptime monitoring.
- **Backblaze B2** free 10GB tier for nightly off-box `pg_dump` backups.
- Basic hardening: ufw firewall, fail2ban, SSH key-only auth,
  unattended-upgrades.
- **Mapbox or MapTiler** free tier for any map display (the one paid-service
  exception, accepted because free-tier volume is sufficient at single-agency
  scale).

Coolify was discussed as an optional deploy-ergonomics layer on top of this;
not required for v1, can be added later without affecting the design.

## 4. Data model

Full entity definitions, field lists, and relationships live in
`/home/claude/crm-data-model.md` (v2) and are the canonical source for this
spec — summarized here for context:

- **Tenancy & identity**: `agency`, `app_user` (ADMIN/MANAGER/AGENT roles,
  individual logins, disable-not-delete for audit continuity).
- **People**: `contact` (buyer/seller/tenant/landlord/lead), with
  `consent_event` as an append-only log rather than a boolean flag.
- **Listings**: `property`, `property_translation` (per-locale title and
  description), `property_media` (a `variants` jsonb map of
  size/format → URL, populated by the image pipeline — not a single `url`
  field; bulk upload is a batch of inserts against one `property_id` in a
  single request).
- **Pipeline**: `deal` (stages LEAD → … → CLOSED_WON/CLOSED_LOST, plus a
  manually-set COLD stage with `cold_reason`/`cold_marked_by_user_id`/
  `cold_marked_at`), `offer` (one current offer per deal, pipeline
  SUBMITTED → COUNTERED → ACCEPTED → UNDER_CONDITIONS → NOTARY_SCHEDULED →
  COMPLETED, with REJECTED/WITHDRAWN as exits), `activity` (logged
  interactions; `activity_type = VIEWING` rows, filtered/grouped by date,
  are the entire data source for the viewings calendar — no separate
  calendar table).
- **Compliance & ops backbone**: `audit_log` (append-only), `document`
  (polymorphic over CONTACT/PROPERTY/DEAL/OFFER), `portal_feed_config`
  (future portal integration), `retention_policy`.

Note for implementation: the original v1 full DDL draft (for `agency`,
`app_user`, `contact`, `property`, `property_translation`, `property_media`,
`audit_log`, `portal_feed_config`, `retention_policy`) was lost to an
environment reset during design and was never rewritten as DDL — only as
the field tables in `crm-data-model.md`. Writing the actual Flyway migrations
from those field tables is part of Phase 2 below, not something this spec
needs to re-derive.

Explicitly deferred, not in v1: a dedicated `offer_document` table (v2 uses
`document` with `related_entity_type = 'OFFER'` instead — revisit if offers
accumulate many documents in practice), application-layer viewing
double-booking checks, commission tracking, and rental-specific fields
(recurring rent, deposit, lease term).

## 5. Image pipeline (cross-cutting requirement)

Images are processed once, server-side, at upload time — not via an
on-demand transform API: strip EXIF/GPS, auto-orient, and generate a fixed
set of sizes (thumbnail ~400px, card ~800px, gallery ~1600px, full-detail
capped ~3000px) each in WebP and JPEG, stored as static files in MinIO, with
the resulting URLs written into `property_media.variants`. The frontend
serves these via `<picture>`/`srcset`/`loading="lazy"`. Java has no native
WebP encoder, so this needs an added library (Thumbnailator for resizing,
plus a WebP encoder) in the Spring Boot backend.

## 6. Feature scope (the four confirmed priorities)

1. **Listings**: create/edit a property with full details, bulk multi-image
   upload processed through the pipeline above.
2. **Leads**: track a contact from first arrival (via a "property request")
   through the `deal` stage pipeline to CLOSED_WON or manually-marked COLD.
3. **Offers**: submit and track an offer on a property through the
   SUBMITTED → … → COMPLETED pipeline, ending at notary signing.
4. **Calendar**: display arranged viewings (in-app only, no external
   calendar sync) from the `activity` table.

## 7. Build phases

Phase 0 (this spec) is done once the implementation plan (produced by the
writing-plans skill, next step after this document is approved) exists.

1. **Project skeleton** — initialize the Spring Boot backend and Next.js
   admin frontend projects; local Postgres via Docker; both run locally
   with no features yet.
2. **Auth, roles, audit log** — individual logins, ADMIN/MANAGER/AGENT
   roles, `audit_log` wired into writes. Built before any feature because
   every other phase depends on "who did this."
3. **Property listings end-to-end** — CRUD, the bulk image upload pipeline,
   list/detail views.
4. **Leads/contacts** — contact capture, deal stage pipeline, cold marking,
   activity logging.
5. **Offers** — the offer entity and its status pipeline.
6. **Viewings calendar** — calendar view over `activity` where
   `activity_type = VIEWING`.
7. **Dashboard UI** — one of the two approved Figma designs (Option A,
   pipeline-first, or Option B, listings-first — file key
   `H5YASMlEWiMEk0zC4PKQFK`) wired to live data as the CRM's home screen.
8. **Testing** — automated tests plus a manual pass against the four
   priority workflows end-to-end.
9. **Deploy** — the Oracle Cloud VM: Docker Compose, Caddy/TLS, Backblaze
   backups, Uptime Kuma monitoring, hardening steps from section 3.

## 8. Explicitly out of scope for this spec

- Replacing the user's existing Next.js website's Sanity CMS integration
  with this CRM. Confirmed approach: build and prove out the CRM standalone
  first (phases above), only then design a separate, additive public
  read-only API on top of it, migrate existing Sanity listings into the
  CRM's database, verify parity, and finally rewrite the website's
  data-fetching layer. This gets its own design pass once the CRM is
  running and trusted.
- Agency-to-agency SaaS billing (Stripe doesn't support Andorra-registered
  businesses directly; Paddle/Lemon Squeezy or Redsys were identified as
  legitimate options if this becomes relevant later — not needed now).
- Portal/lead integration (Idealista, pisos.ad, etc. via XML feed or email
  parsing) — identified as a good fit for Java (JAXB/StAX, Jakarta Mail)
  but not part of the four confirmed priority features.
- Multi-agency tenant isolation enforcement (Postgres RLS) — the schema
  stays tenant-ready (`agency_id` everywhere) but enforcing it is not
  urgent for a single-agency deployment.
