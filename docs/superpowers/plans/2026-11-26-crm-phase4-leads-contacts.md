# CRM Phase 4 — Leads/Contacts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track a lead from manual capture through the deal stage pipeline to a won close or a manually-marked cold lead, with activity logging along the way — plus a non-ADMIN-reachable user directory and closing out Phase 3's deferred `property.owner_contact_id` link.

**Architecture:** Two new packages, `contact` and `deal` (the latter owning `activity` too, since this phase only logs activities against a deal), each mirroring the existing `property`/`user` packages' shape (JPA entity + repository + DTOs + controller, audit-logged the same way). A small addition to the existing `UserController` backs the new agent-picker dropdowns without loosening its existing ADMIN gating. The frontend adds `/contacts` and `/deals` sections following the same page/lib/component split as `/properties`, plus one new field on the existing property edit form.

**Tech Stack:** Spring Boot / Spring Data JPA / Flyway (existing, no new libraries), Next.js / TypeScript / Jest (existing, no new libraries).

**Spec:** `docs/superpowers/specs/2026-11-26-crm-phase4-leads-contacts-design.md`

## Global Constraints

- No role restriction on contacts/deals/activities — any authenticated user may create and edit any of them (design §2).
- `consent_event` is out of scope this phase: no table, no UI (design §2, §8).
- A deal is created only from its contact's page — there is no standalone `/deals/new` creation form (design §2).
- Moving a deal's stage to `COLD` requires `coldReason` in the same request; moving to `CLOSED_LOST` requires `lostReason`. Every other stage transition is unrestricted — no formal state machine (design §2, §4).
- Activities are logged only against a deal, never a bare contact, and are create-only — no edit or delete endpoint (design §2, §8).
- `property.owner_contact_id` is added this phase; the owner `<select>` is not filtered by contact type — any contact can be picked (design §2).
- Every contact/user/property picker in the frontend is a plain `<select>`, not search-as-you-type (design §2).
- `GET /api/users/directory` is new and reachable by any authenticated user, returning only `id`/`fullName`. The existing `GET /api/users` keeps its current `@PreAuthorize("hasRole('ADMIN')")` gating and full field set completely unchanged (design §2, builder check).
- No multi-agency scoping enforcement — same standing deferral as every prior phase (design §8).

## Review Focus

- An `Activity` created with `activityType = VIEWING` and no explicit `status` must default to `SCHEDULED`, not `COMPLETED` — `docs/data-model.md`'s own note ("mainly meaningful for VIEWING; other types default COMPLETED since they're logged after the fact") is easy to miss, and a wrong default would make every freshly-logged future viewing look like it already happened (Task 4).
- Moving a deal out of `COLD` or `CLOSED_LOST` back into an active stage must leave the existing `coldReason`/`coldMarkedByUserId`/`coldMarkedAt`/`lostReason` fields untouched, not null them out — design §2 calls this out explicitly as "leaves the earlier cold_marked_* fields in place as history" (Task 3).
- Re-submitting `stage = COLD` on a deal that is already `COLD` (e.g. only updating the reason text) must not overwrite the original `coldMarkedAt` — only a transition *into* `COLD` from a different stage sets it, preserving when the deal was first marked cold (Task 3).
- A required string field present in the request but blank/whitespace-only (`firstName`/`lastName` on contact create; `coldReason`/`lostReason` on the two special deal transitions) must be rejected with 400, not silently accepted as an empty value — the exact class of bug Phase 3's final review found for `referenceCode`/`caTitle` (Tasks 2, 3).
- The new `GET /api/users/directory` endpoint must never expose `email`, `role`, or `active` to its caller — reusing `UserResponse` instead of a dedicated minimal DTO would silently leak every user's email and role to every other logged-in user (Task 1).

---

### Task 1: User directory endpoint

A small, self-contained addition to the existing `user` package: a non-ADMIN-gated way to list `{id, fullName}` for every active user, which Tasks 6-11's frontend pickers need.

**Files:**
- Modify: `backend/src/main/java/com/sweethomecompany/crm/user/UserController.java` (add one endpoint)
- Create: `backend/src/main/java/com/sweethomecompany/crm/user/UserDirectoryEntry.java`
- Test: `backend/src/test/java/com/sweethomecompany/crm/user/UserControllerTest.java` (add tests)

**Interfaces:**
- Produces: `GET /api/users/directory` → `List<UserDirectoryEntry>`, `UserDirectoryEntry(UUID id, String fullName)`. Tasks 6, 7, 8, and 11's frontend consume this endpoint for their agent pickers (not the record directly — they parse its JSON shape). Task 9's list page only displays `assignedUserFullName` already denormalized onto `DealResponse` — it has no picker and doesn't call this endpoint.

- [ ] **Step 1: Write the failing tests**

```java
// Add to UserControllerTest.java

@Test
void directoryIsReachableByANonAdminUserAndExposesOnlyIdAndFullName() {
    Session admin = loginAs("admin@example.com", "change-me-now");
    String createJson = """
        {"email":"agent-directory@example.com","password":"a-strong-password","fullName":"Agent Directory","role":"AGENT"}""";
    restTemplate.postForEntity("/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);
    Session agent = loginAs("agent-directory@example.com", "a-strong-password");

    ResponseEntity<String> response = restTemplate.exchange(
        "/api/users/directory", HttpMethod.GET, new HttpEntity<>(authedHeaders(agent)), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("Agent Directory");
    assertThat(response.getBody()).doesNotContain("\"email\"", "\"role\"", "\"active\"");
}

@Test
void theExistingUsersEndpointStillRejectsANonAdminCaller() {
    Session admin = loginAs("admin@example.com", "change-me-now");
    String createJson = """
        {"email":"manager-regression@example.com","password":"a-strong-password","fullName":"Manager Regression","role":"MANAGER"}""";
    restTemplate.postForEntity("/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);
    Session manager = loginAs("manager-regression@example.com", "a-strong-password");

    ResponseEntity<String> response = restTemplate.exchange(
        "/api/users", HttpMethod.GET, new HttpEntity<>(authedHeaders(manager)), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=UserControllerTest`
Expected: FAIL — `/api/users/directory` doesn't exist yet (404), and the regression test may already pass (that's fine — it's there to stay green, not to go red).

- [ ] **Step 3: Implement**

`UserDirectoryEntry(UUID id, String fullName)` — a record with a static `from(AppUser)` factory, same shape as `UserResponse.from`.

In `UserController`, add:

```java
@GetMapping("/directory")
@PreAuthorize("isAuthenticated()")
public List<UserDirectoryEntry> directory() {
    return appUserRepository.findAll().stream()
        .filter(AppUser::isActive)
        .map(UserDirectoryEntry::from)
        .toList();
}
```

The method-level `@PreAuthorize("isAuthenticated()")` is required here: Spring Security uses the *method's own* annotation when one is present instead of combining it with the class-level one, so without this the class-level `@PreAuthorize("hasRole('ADMIN')")` would otherwise apply to this method too.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=UserControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/sweethomecompany/crm/user/
git commit -m "Add non-admin user directory endpoint for agent pickers"
```

---

### Task 2: Contact entity, migration, and CRUD endpoints

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__create_contact_deal_activity_tables.sql` (full migration: `contact`, `deal`, `activity`, and the `property.owner_contact_id` column — written in full now per design §3, even though Tasks 3-5 are the first to actually use `deal`/`activity`/the new property column, avoiding a second migration per table)
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/Contact.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/ContactType.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/ContactSource.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/ContactRepository.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/CreateContactRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/UpdateContactRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/ContactResponse.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/contact/ContactController.java`
- Test: `backend/src/test/java/com/sweethomecompany/crm/contact/ContactControllerTest.java`

**Interfaces:**
- Consumes: `AuditLogService.log(...)` (Phase 2, `com.sweethomecompany.crm.audit`), the authenticated `AppUserPrincipal` from the security context (same pattern `PropertyController` uses).
- Produces: `Contact` JPA entity, `ContactRepository extends JpaRepository<Contact, UUID>`, `ContactResponse`. Task 3 (Deal) consumes `ContactRepository.findById` to validate `contactId`. Task 5 (property owner) consumes `ContactRepository.findById` to validate `ownerContactId`.

- [ ] **Step 1: Write the migration**

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

- [ ] **Step 2: Write the failing tests**

```java
class ContactControllerTest {
    @Test
    void creatingAContactWithValidDataSucceedsAndWritesACreateAuditRow() {
        // POST /api/contacts {"contactType":"LEAD","firstName":"Maria","lastName":"Puig",
        //   "source":"WALK_IN"}; assert 201, body has id/contactType/firstName/lastName,
        //   ownerUserId == the acting admin's id (default); assert one AuditLog row,
        //   action=CREATE, entityType="CONTACT"
    }

    @Test
    void creatingWithAnExplicitOwnerUserIdUsesIt() { } // assert ownerUserId == the id sent, not the actor's

    @Test
    void creatingWithoutAContactTypeOrSourceReturns400() { }

    @Test
    void creatingWithABlankFirstNameOrLastNameReturns400() { } // Review Focus

    @Test
    void gettingAnExistingContactReturnsIt() { }

    @Test
    void gettingANonexistentContactReturns404() { }

    @Test
    void listingReturnsAllContacts() { }

    @Test
    void updatingFieldsSucceedsAndWritesAnUpdateAuditRow() { } // e.g. change notes + ownerUserId
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -Dtest=ContactControllerTest`
Expected: FAIL — none of these types/endpoints exist yet.

- [ ] **Step 4: Implement the entity, repository, DTOs, and controller**

`Contact` — JPA entity matching the migration's columns one-for-one.

`ContactType` — enum `BUYER, SELLER, TENANT, LANDLORD, LEAD`. `ContactSource` — enum `PORTAL, WEBSITE, REFERRAL, MANUAL, WALK_IN`.

`CreateContactRequest(ContactType contactType, String firstName, String lastName, String email, String phone, String preferredLocale, ContactSource source, String sourceDetail, String notes, UUID ownerUserId)` — required (400 if missing/blank): `contactType`, `firstName`, `lastName`, `source`. `ownerUserId` is optional.

`UpdateContactRequest` — same fields, all optional, PATCH semantics (only non-null fields applied), matching `UpdatePropertyRequest`'s pattern.

`ContactResponse` — all `Contact` fields plus `id`/`createdAt`/`updatedAt`, with a static `from(Contact)` factory. No embedded deals list — Task 3's `GET /api/deals?contactId=` is how the frontend fetches a contact's deals.

`ContactController`:
- `POST /api/contacts` — validates required fields and that `firstName`/`lastName` aren't blank; defaults `ownerUserId` to `actor.getId()` if omitted; inserts; logs `CREATE` with `entityType = "CONTACT"`.
- `GET /api/contacts` — all contacts (current agency, per the no-scoping constraint) mapped to `ContactResponse`.
- `GET /api/contacts/{id}` — 404 if missing.
- `PATCH /api/contacts/{id}` — applies non-null fields, builds a changed-fields map the same way `PropertyController.update()` does, logs `UPDATE` only if anything changed.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=ContactControllerTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V4__create_contact_deal_activity_tables.sql backend/src/main/java/com/sweethomecompany/crm/contact/ backend/src/test/java/com/sweethomecompany/crm/contact/
git commit -m "Add contact entity, migration, and CRUD endpoints"
```

---

### Task 3: Deal entity and CRUD endpoints, with stage-transition rules

**Files:**
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/Deal.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/DealStage.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/DealRepository.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/CreateDealRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/UpdateDealRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/DealResponse.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/DealController.java`
- Test: `backend/src/test/java/com/sweethomecompany/crm/deal/DealControllerTest.java`

**Interfaces:**
- Consumes: `ContactRepository`/`Contact` (Task 2) to validate `contactId`; the existing `PropertyRepository` (Phase 3) to validate `propertyId` if provided; the existing `AppUserRepository` (Phase 2) to validate `assignedUserId` if provided and to resolve display names.
- Produces: `Deal` JPA entity, `DealRepository extends JpaRepository<Deal, UUID>` with `List<Deal> findByContactId(UUID contactId)`, `DealResponse`. Task 4 (Activity) consumes `DealRepository.findById` and reads `contactId`/`propertyId` off the found `Deal` to populate a new activity.

- [ ] **Step 1: Write the failing tests**

```java
class DealControllerTest {
    @Test
    void creatingADealWithValidDataSucceedsDefaultsStageToLeadAndWritesACreateAuditRow() {
        // create a contact first; POST /api/deals {"contactId": contact.id}; assert 201,
        // stage == "LEAD", assignedUserId == the acting admin's id (default); assert one
        // AuditLog row, action=CREATE, entityType="DEAL"
    }

    @Test
    void creatingWithAnExplicitPropertyIdAndAssignedUserIdUsesThem() { }

    @Test
    void creatingWithANonexistentContactIdReturns404() { }

    @Test
    void creatingWithANonexistentPropertyIdReturns404() { }

    @Test
    void gettingAnExistingDealReturnsItWithContactAndPropertyNames() { } // DealResponse denormalized fields

    @Test
    void gettingANonexistentDealReturns404() { }

    @Test
    void listingReturnsAllDeals() { }

    @Test
    void listingFilteredByContactIdReturnsOnlyThatContactsDeals() { }

    @Test
    void updatingStageToAnOrdinaryValueSucceedsAndWritesAnUpdateAuditRow() { } // e.g. QUALIFIED

    @Test
    void updatingStageToColdWithoutAColdReasonReturns400() { }

    @Test
    void updatingStageToColdWithABlankColdReasonReturns400() { } // Review Focus — blank, not just missing

    @Test
    void updatingStageToColdWithAColdReasonSucceedsAndSetsColdMarkedFields() { }

    @Test
    void updatingStageToClosedLostWithoutALostReasonReturns400() { }

    @Test
    void updatingStageToClosedLostWithABlankLostReasonReturns400() { } // Review Focus — blank, not just missing

    @Test
    void updatingStageToClosedLostWithALostReasonSucceeds() { }

    @Test
    void movingAColdDealBackToAnActiveStageLeavesTheColdFieldsInPlace() { } // Review Focus

    @Test
    void resubmittingColdOnAnAlreadyColdDealDoesNotOverwriteTheOriginalColdMarkedAt() { } // Review Focus
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=DealControllerTest`
Expected: FAIL — none of these types/endpoints exist yet.

- [ ] **Step 3: Implement**

`Deal` — JPA entity matching the migration's columns one-for-one.

`DealStage` — enum `LEAD, QUALIFIED, VIEWING_SCHEDULED, VIEWED, OFFER_MADE, NEGOTIATION, UNDER_CONTRACT, CLOSED_WON, CLOSED_LOST, COLD`.

`CreateDealRequest(UUID contactId, UUID propertyId, UUID assignedUserId, LocalDate expectedCloseDate)` — `contactId` required (400 if null; 404 if it doesn't resolve via `ContactRepository`); `propertyId`/`assignedUserId` optional, 404 if provided but unresolvable; `assignedUserId` defaults to `actor.getId()` if omitted; `stage` is always `LEAD` at creation, not settable here.

`UpdateDealRequest(UUID propertyId, UUID assignedUserId, DealStage stage, String coldReason, String lostReason, LocalDate expectedCloseDate)` — all optional, PATCH semantics.

`DealResponse` — `id, contactId, contactName, propertyId, propertyReferenceCode, assignedUserId, assignedUserFullName, stage, lostReason, coldReason, coldMarkedByUserId, coldMarkedAt, expectedCloseDate, createdAt, updatedAt`, built by the controller from the `Deal` plus one `ContactRepository`/`PropertyRepository`/`AppUserRepository` lookup each (small dataset, no pagination needed at this scale — same assumption the rest of this project already makes).

`DealController`:
- `POST /api/deals` — validates per above; inserts with `stage = LEAD`; logs `CREATE` with `entityType = "DEAL"`.
- `GET /api/deals` — optional `@RequestParam(required = false) UUID contactId`; `findByContactId` if present, else `findAll()`.
- `GET /api/deals/{id}` — 404 if missing.
- `PATCH /api/deals/{id}` — applies `propertyId`/`assignedUserId`/`expectedCloseDate` like any other field (404 if a provided id doesn't resolve), builds a changed-fields map like `PropertyController.update()`. `stage` gets special handling:
  - If the new `stage == COLD`: reject with 400 unless `coldReason` is present and non-blank in this same request. On success, set `coldReason` from the request; set `coldMarkedByUserId`/`coldMarkedAt` to the acting user/now **only if the deal's stage was not already `COLD`** (Review Focus: re-submitting `COLD` on an already-`COLD` deal must not reset `coldMarkedAt`).
  - If the new `stage == CLOSED_LOST`: reject with 400 unless `lostReason` is present and non-blank in this same request; set `lostReason` from the request.
  - Any other new `stage` value: set it with no extra validation and no change to `coldReason`/`coldMarkedByUserId`/`coldMarkedAt`/`lostReason` — those fields are left exactly as they were (Review Focus: moving a deal back out of `COLD`/`CLOSED_LOST` preserves that history).
  - Log `UPDATE` only if anything changed.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=DealControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/sweethomecompany/crm/deal/ backend/src/test/java/com/sweethomecompany/crm/deal/DealControllerTest.java
git commit -m "Add deal entity and CRUD endpoints with cold/lost stage transition rules"
```

---

### Task 4: Activity logging, nested under deals

**Files:**
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/Activity.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/ActivityType.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/ActivityStatus.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/ActivityRepository.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/CreateActivityRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/deal/ActivityResponse.java`
- Modify: `backend/src/main/java/com/sweethomecompany/crm/deal/DealController.java` (add the two nested endpoints)
- Test: `backend/src/test/java/com/sweethomecompany/crm/deal/ActivityControllerTest.java`

**Interfaces:**
- Consumes: `Deal`/`DealRepository` (Task 3) to resolve the owning deal and inherit its `contactId`/`propertyId`; the existing `AppUserRepository` to resolve the acting user's display name.
- Produces: `Activity` JPA entity, `ActivityRepository extends JpaRepository<Activity, UUID>` with `List<Activity> findByDealIdOrderByOccurredAtDesc(UUID dealId)`.

- [ ] **Step 1: Write the failing tests**

```java
class ActivityControllerTest {
    @Test
    void loggingACallAgainstADealSucceedsInheritsContactAndPropertyAndWritesACreateAuditRow() {
        // create a contact + a deal (with a propertyId) for it; POST
        // /api/deals/{dealId}/activities {"activityType":"CALL","notes":"Left a
        // voicemail","occurredAt":"2026-11-26T10:00:00Z"}; assert 201, contactId/
        // propertyId on the response match the deal's, userId == acting user, status
        // defaults to "COMPLETED"; assert one AuditLog row, action=CREATE,
        // entityType="ACTIVITY"
    }

    @Test
    void loggingAViewingWithNoExplicitStatusDefaultsToScheduledNotCompleted() { } // Review Focus

    @Test
    void anExplicitStatusOverridesTheDefault() { } // e.g. a VIEWING logged as COMPLETED after the fact

    @Test
    void loggingWithoutAnActivityTypeOrOccurredAtReturns400() { }

    @Test
    void loggingAgainstANonexistentDealReturns404() { }

    @Test
    void listingADealsActivitiesReturnsThemNewestFirst() { }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ActivityControllerTest`
Expected: FAIL — none of these types/endpoints exist yet.

- [ ] **Step 3: Implement**

`Activity` — JPA entity matching the migration's columns one-for-one.

`ActivityType` — enum `CALL, EMAIL, VIEWING, MEETING, NOTE, WHATSAPP`. `ActivityStatus` — enum `SCHEDULED, COMPLETED, CANCELLED, NO_SHOW`.

`CreateActivityRequest(ActivityType activityType, String notes, Instant occurredAt, ActivityStatus status)` — `activityType` and `occurredAt` required (400 if either is null); `status` optional — when omitted, default to `ActivityStatus.SCHEDULED` if `activityType == VIEWING`, else `ActivityStatus.COMPLETED` (Review Focus — this is the one place the data model's "mainly meaningful for VIEWING" note becomes code).

`ActivityResponse(UUID id, UUID dealId, UUID contactId, UUID propertyId, UUID userId, String userFullName, ActivityType activityType, ActivityStatus status, String notes, Instant occurredAt, Instant createdAt)`.

Add to `DealController`:
- `POST /api/deals/{id}/activities` — 404 if the deal doesn't exist; validates per above; `contactId`/`propertyId` come from the resolved `Deal`, not the request body; `userId` is always `actor.getId()`; logs `CREATE` with `entityType = "ACTIVITY"`.
- `GET /api/deals/{id}/activities` — 404 if the deal doesn't exist; else `findByDealIdOrderByOccurredAtDesc`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=ActivityControllerTest`
Expected: PASS

- [ ] **Step 5: Run the full backend suite**

Run: `mvn test`
Expected: PASS — confirms Tasks 1-4 together, plus Phases 1-3's existing tests, are all still green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sweethomecompany/crm/deal/ backend/src/test/java/com/sweethomecompany/crm/deal/ActivityControllerTest.java
git commit -m "Add deal-scoped activity logging with type-aware status default"
```

---

### Task 5: Property owner-contact wiring

Closes out Phase 3's deferred `property.owner_contact_id` — the column already exists from Task 2's migration; this task is purely the application-layer wiring.

**Files:**
- Modify: `backend/src/main/java/com/sweethomecompany/crm/property/Property.java` (add `ownerContactId` field)
- Modify: `backend/src/main/java/com/sweethomecompany/crm/property/CreatePropertyRequest.java` (add `ownerContactId`)
- Modify: `backend/src/main/java/com/sweethomecompany/crm/property/UpdatePropertyRequest.java` (add `ownerContactId`)
- Modify: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyResponse.java` (add `ownerContactId`)
- Modify: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyController.java` (validate + apply `ownerContactId` in `create`/`update`)
- Test: `backend/src/test/java/com/sweethomecompany/crm/property/PropertyControllerTest.java` (add tests)

**Interfaces:**
- Consumes: `ContactRepository` (Task 2) to validate `ownerContactId`.

- [ ] **Step 1: Write the failing tests**

```java
// Add to PropertyControllerTest.java

@Test
void creatingWithAnOwnerContactIdSucceedsAndItIsReflectedInTheResponse() {
    // create a contact first; POST /api/properties with ownerContactId set; assert 201,
    // response contains the ownerContactId
}

@Test
void creatingWithANonexistentOwnerContactIdReturns404() { }

@Test
void updatingOwnerContactIdSucceedsAndWritesAnUpdateAuditRow() { }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=PropertyControllerTest`
Expected: FAIL — `ownerContactId` isn't wired up anywhere yet.

- [ ] **Step 3: Implement**

Add an `ownerContactId` (`UUID`, nullable, `@Column(name = "owner_contact_id")`) field with its getter/setter to `Property`, following the same per-field style already used for its other columns.

Add `ownerContactId` as the last field on both `CreatePropertyRequest` and `UpdatePropertyRequest`, and as a field on `PropertyResponse` (populated from `property.getOwnerContactId()`).

In `PropertyController.create()` and `.update()`: if `ownerContactId` is present in the request, validate it via `contactRepository.findById(...)` (404 "Owner contact not found" if it doesn't resolve) before setting it on the entity; `update()` adds it to the changed-fields map the same way every other optional field there already is. Inject `ContactRepository` into `PropertyController`'s constructor alongside its existing dependencies.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=PropertyControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/sweethomecompany/crm/property/ backend/src/test/java/com/sweethomecompany/crm/property/PropertyControllerTest.java
git commit -m "Wire property.ownerContactId into create/update endpoints"
```

---

### Task 6: Frontend — contacts list page, create form, and nav links

**Files:**
- Create: `frontend/lib/contacts.ts`
- Create: `frontend/lib/users.ts` (the new directory endpoint's client)
- Create: `frontend/app/contacts/page.tsx`
- Test: `frontend/app/contacts/page.test.tsx`
- Create: `frontend/app/contacts/new/page.tsx`
- Test: `frontend/app/contacts/new/page.test.tsx`
- Modify: `frontend/app/components/NavBar.tsx` (add "Contacts" and "Deals" links)
- Modify: `frontend/app/components/NavBar.test.tsx`

**Interfaces:**
- Produces: `fetchUserDirectory(): Promise<UserDirectoryEntry[]>` in `frontend/lib/users.ts`, where `UserDirectoryEntry = { id: string, fullName: string }`. Consumed by this task's own create form and by Tasks 7, 8, 11.
- Produces: `fetchContacts(): Promise<ContactSummary[]>`, `createContact(data: CreateContactInput): Promise<{ id: string }>` in `frontend/lib/contacts.ts`, where `ContactSummary`/`CreateContactInput` mirror the backend's `ContactResponse`/`CreateContactRequest` fields. Tasks 7-8 extend `contacts.ts` and reuse these types.

- [ ] **Step 1: Write the failing tests**

```tsx
// NavBar.test.tsx: add two cases
it("shows a Contacts link for any logged-in user", () => { });
it("shows a Deals link for any logged-in user", () => { });
```

```tsx
// contacts/page.test.tsx
describe("Contacts list page", () => {
  it("renders each contact's name, type, source, and owner agent", () => { });
  it("shows a New contact link to /contacts/new", () => { });
  it("shows an empty state when there are no contacts yet", () => { });
});
```

```tsx
// contacts/new/page.test.tsx
describe("New contact page", () => {
  it("submits all entered fields and redirects to the new contact's detail page", () => { }); // router.push(`/contacts/${id}`)
  it("shows a validation error when first name, last name, type, or source is missing", () => { });
  it("defaults the owner-agent select to the current user and lets a different one be picked", () => { });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- contacts NavBar`
Expected: FAIL

- [ ] **Step 3: Implement**

`frontend/lib/users.ts` — `fetchUserDirectory()`: `GET /api/users/directory` with `credentials: "include"`.

`frontend/lib/contacts.ts` — `fetchContacts()`: `GET /api/contacts`. `createContact(data)`: `POST /api/contacts` with the CSRF header, same pattern as `createProperty`.

`contacts/page.tsx` — client component, fetch-on-mount (matching `/properties`'s real pattern, not the brief-paraphrase "server component" wording Phase 3 already corrected once): a table of contacts (name, type, source, owner agent), a "New contact" link, an empty-state message.

`contacts/new/page.tsx` — client component form with inputs for every `CreateContactRequest` field (`contactType`/`source` as `<select>`s over their enum values from the design doc's data model section); an owner-agent `<select>` populated from `fetchUserDirectory()`, defaulting to the logged-in user's own id (available via the existing `useAuth`/`AuthContext`, same as every other page that needs "the current user"); client-side required-field check for `firstName`/`lastName`/`contactType`/`source`; on success, `router.push`'s to `/contacts/{id}`.

Add "Contacts" (`/contacts`) and "Deals" (`/deals`) links to `NavBar.tsx`, unconditionally (no role check, per Global Constraints).

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- contacts NavBar`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/contacts.ts frontend/lib/users.ts frontend/app/contacts/ frontend/app/components/NavBar.tsx frontend/app/components/NavBar.test.tsx
git commit -m "Add contacts list page, create form, and nav links"
```

---

### Task 7: Frontend — contact detail and edit pages

**Files:**
- Create: `frontend/app/contacts/[id]/page.tsx`
- Create: `frontend/app/contacts/[id]/ContactDetailView.tsx`
- Test: `frontend/app/contacts/[id]/page.test.tsx`
- Create: `frontend/app/contacts/[id]/edit/page.tsx`
- Create: `frontend/app/contacts/[id]/edit/ContactEditView.tsx`
- Test: `frontend/app/contacts/[id]/edit/page.test.tsx`
- Modify: `frontend/lib/contacts.ts` (add `fetchContact`, `updateContact`)

**Interfaces:**
- Produces: `fetchContact(id): Promise<ContactDetail>`, `updateContact(id, data: UpdateContactInput): Promise<ContactDetail>` in `frontend/lib/contacts.ts`. Task 8 consumes `ContactDetail`.

- [ ] **Step 1: Write the failing tests**

```tsx
// contacts/[id]/page.test.tsx — tests ContactDetailView directly, same reason
// Phase 3's PropertyDetailView split exists (this app's cacheComponents: true
// needs a Suspense boundary around dynamic `params`, and every page fetches
// client-side with credentials: "include")
describe("Contact detail view", () => {
  it("renders every field and the owner agent's name", () => { });
  it("shows an Edit link to /contacts/{id}/edit", () => { });
  it("shows a New deal link to /contacts/{id}/deals/new", () => { });
  it("lists the contact's deals fetched via GET /api/deals?contactId=", () => { });
});
```

```tsx
// contacts/[id]/edit/page.test.tsx — tests ContactEditView directly
describe("Contact edit view", () => {
  it("loads the contact's current values into the form", () => { });
  it("submits only the changed fields and shows a saved confirmation", () => { });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- "contacts/\[id\]"`
Expected: FAIL

- [ ] **Step 3: Implement**

Add to `contacts.ts`: `fetchContact(id)` (`GET /api/contacts/{id}`), `updateContact(id, data)` (`PATCH /api/contacts/{id}`), and a `fetchDealsForContact(contactId)` helper (`GET /api/deals?contactId=`) returning `DealSummary[]` — a minimal local type here (`id`, `stage`, `propertyReferenceCode`, `assignedUserFullName`) good enough for this list; Task 9 defines the fuller `DealSummary` type used by the pipeline list page, and this task's type can be widened then if it turns out not to already match.

`contacts/[id]/page.tsx` + `ContactDetailView.tsx` — same wrapper/view split as `properties/[id]`: full field display, owner agent's name, a "New deal" link to `/contacts/{id}/deals/new`, an "Edit" link, and a list of the contact's deals (stage, property reference if any, assigned agent) each linking to `/deals/{dealId}`.

`contacts/[id]/edit/page.tsx` + `ContactEditView.tsx` — same wrapper/view split and changed-field-tracking pattern as `PropertyEditView`: all `UpdateContactRequest` fields, including the owner-agent `<select>`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- "contacts/\[id\]"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/contacts.ts "frontend/app/contacts/[id]/"
git commit -m "Add contact detail and edit pages"
```

---

### Task 8: Frontend — contact-scoped deal creation form

**Files:**
- Create: `frontend/lib/deals.ts`
- Create: `frontend/app/contacts/[id]/deals/new/page.tsx`
- Test: `frontend/app/contacts/[id]/deals/new/page.test.tsx`

**Interfaces:**
- Consumes: `fetchUserDirectory` (Task 6), `ContactDetail` (Task 7).
- Produces: `createDeal(data: CreateDealInput): Promise<{ id: string }>` in `frontend/lib/deals.ts`, where `CreateDealInput` mirrors the backend's `CreateDealRequest`. Tasks 9-11 extend `deals.ts` and reuse this type.

- [ ] **Step 1: Write the failing test**

```tsx
describe("New deal page", () => {
  it("submits the deal scoped to this contact and redirects to its detail page", () => { }); // router.push(`/deals/${id}`)
  it("lets an existing property be picked from a select, or left unset", () => { });
  it("defaults the assigned-agent select to the current user", () => { });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- "deals/new"`
Expected: FAIL

- [ ] **Step 3: Implement**

`frontend/lib/deals.ts` — `createDeal(data)`: `POST /api/deals` with the CSRF header.

`contacts/[id]/deals/new/page.tsx` — client component form: `contactId` comes from the route param, not a form field; a property `<select>` (options from the existing `fetchProperties()` in `frontend/lib/properties.ts`, by reference code, with a blank "none" option) and an assigned-agent `<select>` (from `fetchUserDirectory()`, defaulting to the current user), plus an optional expected-close-date input; on success, `router.push`'s to `/deals/{id}`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- "deals/new"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/deals.ts "frontend/app/contacts/[id]/deals/"
git commit -m "Add contact-scoped deal creation form"
```

---

### Task 9: Frontend — deals pipeline list page

**Files:**
- Create: `frontend/app/deals/page.tsx`
- Test: `frontend/app/deals/page.test.tsx`
- Modify: `frontend/lib/deals.ts` (add `fetchDeals`, the full `DealSummary` type)

**Interfaces:**
- Produces: `fetchDeals(): Promise<DealSummary[]>` in `frontend/lib/deals.ts`, where `DealSummary` mirrors the backend's `DealResponse` fields (`id`, `contactName`, `propertyReferenceCode`, `stage`, `assignedUserFullName`, `expectedCloseDate`). Task 10 extends this with the full detail type.

- [ ] **Step 1: Write the failing test**

```tsx
describe("Deals pipeline list page", () => {
  it("renders each deal's contact name, property reference, stage, assigned agent, and expected close date", () => { });
  it("sorts the list by stage", () => { });
  it("shows an empty state when there are no deals yet", () => { });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- deals/page`
Expected: FAIL

- [ ] **Step 3: Implement**

`fetchDeals()` in `deals.ts` — `GET /api/deals`.

`deals/page.tsx` — client component, fetch-on-mount: a table sorted by `stage` (use the `DealStage` enum's declaration order — `LEAD` through `COLD` as listed in the backend's `DealStage.java` — as the sort key, since that's the only ordering the spec implies), each row linking to `/deals/{id}`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- deals/page`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/deals.ts frontend/app/deals/page.tsx frontend/app/deals/page.test.tsx
git commit -m "Add deals pipeline list page"
```

---

### Task 10: Frontend — deal detail page, stage actions, and activity log

**Files:**
- Create: `frontend/app/deals/[id]/page.tsx`
- Create: `frontend/app/deals/[id]/DealDetailView.tsx`
- Test: `frontend/app/deals/[id]/page.test.tsx`
- Modify: `frontend/lib/deals.ts` (add `fetchDeal`, `updateDeal`, `fetchActivities`, `logActivity`)

**Interfaces:**
- Produces: `fetchDeal(id): Promise<DealDetail>`, `updateDeal(id, data): Promise<DealDetail>`, `fetchActivities(dealId): Promise<ActivityEntry[]>`, `logActivity(dealId, data: LogActivityInput): Promise<ActivityEntry>` in `frontend/lib/deals.ts`. Task 11 consumes `DealDetail`.

- [ ] **Step 1: Write the failing tests**

```tsx
// Tests DealDetailView directly, same wrapper/view split reasoning as contacts
describe("Deal detail view", () => {
  it("renders the deal's fields and an Edit link to /deals/{id}/edit", () => { });
  it("changing the stage select to an ordinary value saves it immediately", () => { }); // e.g. QUALIFIED
  it("Mark as cold prompts for a reason and saves stage=COLD with it", () => { });
  it("does not submit Mark as cold if the reason prompt is cancelled or left blank", () => { });
  it("Close as lost prompts for a reason and saves stage=CLOSED_LOST with it", () => { });
  it("logs a new activity via the mini-form and shows it at the top of the activity list", () => { });
  it("a logged VIEWING activity with no status chosen shows as SCHEDULED", () => { }); // Review Focus, frontend side
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- "deals/\[id\]"`
Expected: FAIL

- [ ] **Step 3: Implement**

Add to `deals.ts`: `fetchDeal(id)` (`GET /api/deals/{id}`), `updateDeal(id, data)` (`PATCH /api/deals/{id}`), `fetchActivities(dealId)` (`GET /api/deals/{id}/activities`), `logActivity(dealId, data)` (`POST /api/deals/{id}/activities`).

`deals/[id]/page.tsx` + `DealDetailView.tsx` — same wrapper/view split as `properties/[id]`: full field display, a `stage` `<select>` that calls `updateDeal` on change for ordinary values; two buttons, "Mark as cold" and "Close as lost", each using `window.prompt` (or an inline text input shown on click — implementer's call, either is fine) to collect the required reason before calling `updateDeal(id, { stage: "COLD", coldReason })` / `updateDeal(id, { stage: "CLOSED_LOST", lostReason })`, and not calling it at all if the reason is cancelled or left blank; an "Edit" link to `/deals/{id}/edit`; an activity section with a mini-form (type `<select>`, notes, an `occurredAt` datetime input defaulting to now, an optional status `<select>`) calling `logActivity` on submit and a chronological list below it rendering `fetchActivities`'s result.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- "deals/\[id\]"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/deals.ts "frontend/app/deals/[id]/page.tsx" "frontend/app/deals/[id]/DealDetailView.tsx" "frontend/app/deals/[id]/page.test.tsx"
git commit -m "Add deal detail page with stage actions and activity log"
```

---

### Task 11: Frontend — deal edit page and the property edit page's owner field

**Files:**
- Create: `frontend/app/deals/[id]/edit/page.tsx`
- Create: `frontend/app/deals/[id]/edit/DealEditView.tsx`
- Test: `frontend/app/deals/[id]/edit/page.test.tsx`
- Modify: `frontend/app/properties/[id]/edit/PropertyEditView.tsx` (add the owner-contact field)
- Modify: `frontend/app/properties/[id]/edit/page.test.tsx`
- Modify: `frontend/lib/properties.ts` (add `ownerContactId` to the relevant types)

**Interfaces:**
- Consumes: `fetchUserDirectory` (Task 6), `fetchProperties` (Phase 3), `DealDetail` (Task 10), `fetchContacts` (Task 6).

- [ ] **Step 1: Write the failing tests**

```tsx
// deals/[id]/edit/page.test.tsx — tests DealEditView directly
describe("Deal edit view", () => {
  it("loads the deal's current property, assigned agent, and expected close date into the form", () => { });
  it("submits only the changed fields and shows a saved confirmation", () => { });
});
```

```tsx
// Add to properties/[id]/edit/page.test.tsx
it("renders an Owner select populated from fetched contacts and submits the chosen id", () => { });
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- "deals/\[id\]/edit" "properties/\[id\]/edit"`
Expected: FAIL

- [ ] **Step 3: Implement**

`deals/[id]/edit/page.tsx` + `DealEditView.tsx` — same wrapper/view split and changed-field-tracking pattern as `PropertyEditView`, scoped to `propertyId` (a `<select>` from `fetchProperties()`), `assignedUserId` (a `<select>` from `fetchUserDirectory()`), and `expectedCloseDate`. `stage`/`coldReason`/`lostReason` are deliberately not on this form — they're only ever changed through the detail page's stage select and cold/lost actions (Task 10).

In `PropertyEditView.tsx`: add an "Owner" `<select>` populated from `fetchContacts()` (Task 6), with a blank "none" option, wired into the existing `fields`/`changed` state the same way every other field there already is.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- "deals/\[id\]/edit" "properties/\[id\]/edit"`
Expected: PASS

- [ ] **Step 5: Run the full frontend suite**

Run: `npm test`
Expected: PASS — confirms Tasks 6-11 together, plus Phases 1-3's existing tests, are all still green.

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/properties.ts "frontend/app/deals/[id]/edit/" "frontend/app/properties/[id]/edit/"
git commit -m "Add deal edit page and the property edit page's owner-contact field"
```

---

### Task 12: Manual full-stack verification

No new code — this is the end-to-end human check, same spirit as every prior phase's final manual pass.

- [ ] **Step 1:** Run `docker compose up -d`, start the backend and frontend.
- [ ] **Step 2:** Log in, go to Contacts, create a new contact (a walk-in lead).
- [ ] **Step 3:** From that contact's page, create a deal linked to an existing property.
- [ ] **Step 4:** On the deal's detail page, log a call and a scheduled viewing (confirm the viewing shows as Scheduled, not Completed, without picking a status).
- [ ] **Step 5:** Move the deal through a couple of ordinary stages via the dropdown, then mark a different deal cold with a reason, and confirm the Deals list reflects both.
- [ ] **Step 6:** On an existing property's edit page, set its Owner to the new contact, save, and confirm it's reflected after a reload.
- [ ] **Step 7:** Log in as a non-admin user (or create one first) and confirm the Contacts/Deals pages and their agent pickers all work for them too — this is the real-world check for the Global Constraint that none of this phase is role-gated.
- [ ] **Step 8:** Report back: confirm both full test suites are still green.

---

## Self-Review Notes

**Spec coverage:** Design §3 migration → Task 2 (full file) with Tasks 3-5 as the first consumers of `deal`/`activity`/`property.owner_contact_id`; §4 stage transitions → Task 3; §5 backend components → Tasks 1-5; §6 frontend → Tasks 6-11; §7 testing → woven into each task plus Task 12's manual pass. §8's exclusions (`consent_event`, `offer`, the viewings calendar, a Kanban board, role gating, activity edit/delete, `document`, multi-agency RLS, a formal stage state machine, search-as-you-type pickers) are named in Global Constraints so no task accidentally builds them.

**Type consistency:** `ContactResponse`/`DealResponse`/`ActivityResponse` (Tasks 2-4, backend) line up with `ContactSummary`/`ContactDetail`/`DealSummary`/`DealDetail`/`ActivityEntry` (Tasks 6-11, frontend) field-for-field. `UserDirectoryEntry` (Task 1) is consumed identically by every frontend task that needs an agent picker (6, 8, 9, 11).

**Review Focus:** all five lines have an owning task and test, listed above.
