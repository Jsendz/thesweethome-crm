# CRM Phase 2 — Auth, Roles, and Audit Log (design)

## 1. Purpose and context

Phase 1 produced a Spring Boot backend and Next.js frontend that run
locally and can see each other (health check, CORS). Neither has any
notion of a user yet — anyone who can reach the backend can reach
everything on it.

Per the foundation spec's build-phase ordering, this phase (auth, roles,
audit log) comes before any business feature, because every later phase's
writes need to know "who did this." Concretely, this phase adds: individual
logins backed by the `app_user` table, three roles (ADMIN, MANAGER, AGENT)
enforced on the one set of endpoints this phase introduces (user
management), and an `audit_log` table with a service that later phases
will call into for their own writes.

Source material: `docs/data-model.md` (v2, canonical field tables for
`agency`, `app_user`, `audit_log`) and
`docs/superpowers/specs/2026-11-06-crm-foundation-design.md` (overall
architecture, build-phase ordering).

## 2. Decisions made during brainstorming

- **Auth mechanism: session cookie**, not JWT. Spring Security form login
  sets an `HttpOnly`, `SameSite=Lax` session cookie on successful login;
  the frontend does nothing special beyond sending `credentials: "include"`
  on every request. Sessions are in-memory in the backend process (no
  database-backed session store) — correct for a single backend instance
  on one VM; revisit only if the backend is ever horizontally scaled.
- **User provisioning: admin-created only**, no public sign-up page. An
  ADMIN account creates every MANAGER/AGENT account by hand.
- **First admin account: seeded at startup**, not via a SQL migration (a
  migration can't read environment variables or hash a password). A
  Spring `ApplicationRunner` checks on every boot whether `app_user` is
  empty; if so, it creates one ADMIN account from `ADMIN_EMAIL` /
  `ADMIN_PASSWORD` environment variables, bcrypt-hashed. If a user already
  exists, it does nothing — safe to leave running on every startup forever.
- **Audit log gets a `LOGIN` and `LOGIN_FAILED` action**, extending the
  data model's original `VIEW, CREATE, UPDATE, DELETE, EXPORT` list to
  `VIEW, CREATE, UPDATE, DELETE, EXPORT, LOGIN, LOGIN_FAILED`.
- **Audit logging is explicit, not automatic.** Each place that needs to
  write an audit entry calls `AuditLogService.log(...)` directly, in the
  same method where the action happens. No AOP interceptor, no annotation
  that triggers logging behind the scenes. Chosen over an automatic
  interceptor for this phase because it's simpler to read and debug,
  important given both the project owner and future implementers (Claude
  Code sessions) are not deeply familiar with Spring internals. An
  automatic approach can be introduced later if manual calls become
  genuinely tedious across many services — not needed yet (YAGNI).
- **CSRF protection stays on, via a cookie-readable token.** Session-cookie
  auth makes the app CSRF-vulnerable by default if CSRF protection is
  disabled, so it isn't disabled. Spring Security's cookie-based CSRF
  repository sets a second, non-`HttpOnly` cookie containing a token; the
  frontend reads that cookie in JS and sends it back as an
  `X-XSRF-TOKEN` header on every state-changing request (including the
  login POST itself).
- **The Phase 1 health page is not gated.** It stays publicly reachable
  for now, consistent with Phase 1's behavior; access control on
  `/actuator/**` at the network level (not the application auth level) is
  deferred to Phase 9 (deploy/hardening).

## 3. Architecture / request flow

```
Browser (Next.js, localhost:3000)
  → POST /api/auth/login { email, password }  (+ X-XSRF-TOKEN header)
  ← Set-Cookie: SESSION=... (HttpOnly); Set-Cookie: XSRF-TOKEN=... (readable)

Browser → any subsequent request, with both cookies attached automatically
  → Spring Security reads SESSION cookie, loads the authenticated user
    into the security context for that request
  → Controller/service code can ask "who is the current user, what's
    their role" via that context
```

Spring Security is configured for form-based login against
`/api/auth/login`, backed by a `UserDetailsService` that loads from a new
`AppUserRepository` (Spring Data JPA). Each user's `role` column
(`ADMIN`/`MANAGER`/`AGENT`) maps to a Spring authority
(`ROLE_ADMIN`/`ROLE_MANAGER`/`ROLE_AGENT`), so controller methods can be
annotated `@PreAuthorize("hasRole('ADMIN')")`.

CORS (added in Phase 1 for `/api/**`) gets `allowCredentials(true)` added,
which is what permits the session cookie to cross the
`localhost:3000` → `localhost:8081` origin boundary during local dev. This
stops being a cross-origin concern once both are served from the same
Caddy host in Phase 9.

## 4. Data model / migrations

Two Flyway migrations, the first real ones in this project (Phase 1 had
Flyway wired but nothing to run):

- `V1__create_agency_app_user_audit_log.sql` — creates `agency`,
  `app_user`, and `audit_log` exactly per `docs/data-model.md`'s field
  tables, with `audit_log.action` as the 7-value enum listed above. Seeds
  exactly one `agency` row for "The Sweet Home Company," with a fixed,
  known UUID (so later code and migrations can reference it directly
  without a lookup query).

No migration creates the admin user — see the `ApplicationRunner`
described in section 2 and 5. This keeps secret-dependent logic (reading
env vars, hashing a password) out of SQL entirely.

## 5. Backend components

- **`SecurityConfig`** — Spring Security filter chain: form login at
  `/api/auth/login`, logout at `/api/auth/logout`, session-based (not
  stateless), CORS with `allowCredentials(true)`, CSRF via
  `CookieCsrfTokenRepository.withHttpOnlyFalse()`. A `BCryptPasswordEncoder`
  bean, used both by the admin-seeding runner and by Spring Security's
  authentication provider during login.
- **`AppUserDetailsService`** (`UserDetailsService`) — loads an `app_user`
  by email via `AppUserRepository`, maps `role` to a Spring authority, maps
  `active = false` to a locked/disabled account (Spring Security rejects
  login for a disabled account automatically).
- **`AdminUserSeeder`** (`ApplicationRunner`) — on every startup, if
  `AppUserRepository.count() == 0`, creates one ADMIN user from
  `ADMIN_EMAIL`/`ADMIN_PASSWORD` env vars (hashed). Otherwise a no-op.
- **`AuthController`** — `GET /api/auth/me`, returning the current
  session's user (id, email, full name, role) or 401 if there is none.
  Login/logout themselves are handled by Spring Security's own filters,
  not custom controller methods. The login success/failure handlers
  explicitly call `AuditLogService.log(LOGIN or LOGIN_FAILED, ...)`.
- **`UserController`** — `@PreAuthorize("hasRole('ADMIN')")` on the whole
  controller. `POST /api/users` (create — hashes the password, calls
  `AuditLogService.log(CREATE, ...)`), `GET /api/users` (list, same
  agency), `PATCH /api/users/{id}` (edit fields or toggle `active`, calls
  `AuditLogService.log(UPDATE, ...)`). No delete endpoint, matching the
  data model's disable-not-delete rule.
- **`AuditLogService`** — one method, `log(action, entityType, entityId,
  detail)`, resolving `agency_id` and `actor_user_id` from the current
  security context and writing one `audit_log` row. Called explicitly
  from the four places above; no interceptor, no annotation-driven magic.

## 6. Frontend components

- **`/login` page** — email/password form. On submit: read the
  `XSRF-TOKEN` cookie, POST to `/api/auth/login` with `credentials:
  "include"` and the token as an `X-XSRF-TOKEN` header. Success redirects
  to the dashboard (currently just Phase 1's health page); failure shows
  an inline error.
- **Auth check** — on every other page, a check against `GET
  /api/auth/me` before rendering; no valid session redirects to `/login`.
  The result (name, email, role) is held in a small React context so the
  rest of the app can read "who's logged in, what's their role" without
  re-fetching.
- **`/users` page** — ADMIN-only (hidden/redirected for other roles).
  Lists existing users, a form to create a new one (email, name, role),
  and a toggle to deactivate/reactivate. All mutating calls include the
  `X-XSRF-TOKEN` header the same way login does.
- **Logout** — a button that POSTs to `/api/auth/logout`, clears the local
  auth context, redirects to `/login`.

## 7. Testing

Backend (run on the user's machine via their local Claude Code instance,
same pattern as Phase 1, since Maven Central is unreachable from every
Claude-mediated sandbox):

- Login with correct credentials succeeds and writes a `LOGIN` audit row;
  wrong credentials fail (401) and write a `LOGIN_FAILED` row.
- A non-admin calling any `/api/users` endpoint gets 403.
- An admin creating a user succeeds, the stored password is hashed (not
  plaintext), and a `CREATE` audit row is written.
- Deactivating a user updates the record, writes an `UPDATE` audit row,
  and that user can no longer log in afterward.
- `AdminUserSeeder` creates exactly one admin when the table is empty and
  does nothing on a second run (idempotency).

Frontend (self-verified directly in the cloud sandbox, since Node is
unaffected by the Maven Central block):

- Login form: submits with the CSRF header, redirects on success, shows
  an error on failure.
- Auth check: redirects to `/login` when there's no session.
- `/users` page: renders the list and create form for an ADMIN context,
  is hidden for a MANAGER/AGENT context.
- Logout: posts to the right endpoint and clears local state.

One manual full-stack pass, same spirit as Phase 1's Task 4: log in as the
seeded admin, create a second user, log out, log back in as that user,
confirm the `/users` page is hidden for them.

## 8. Explicitly out of scope for this phase

- Password reset / "forgot password" flow — ADMIN can deactivate and
  recreate an account if needed; a real reset flow needs email sending,
  which isn't wired up yet.
- Any granular permission difference between MANAGER and AGENT — both are
  just "not ADMIN" for this phase's one ADMIN-gated feature (user
  management). Real MANAGER vs. AGENT distinctions will emerge once
  Phase 3+ features exist to need them.
- A UI for viewing the audit log itself — it's written to in this phase,
  but nothing reads it back yet. Revisit once there's enough real audit
  data to make a viewer worth building.
- Login rate-limiting / account lockout after repeated failed attempts,
  and two-factor authentication — worth real consideration before this
  system is reachable from the public internet (Phase 9), not before.
- Multi-tenancy beyond the single seeded agency — `agency_id` exists on
  every row per the data model, but nothing in this phase builds UI or
  logic for managing more than one agency.
