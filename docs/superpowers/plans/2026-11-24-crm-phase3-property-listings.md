# CRM Phase 3 — Property Listings End-to-End Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Full CRUD on property listings — including the bulk multi-image upload pipeline (resize, encode, store in MinIO) and admin list/detail/create/edit screens — building on Phase 2's auth and audit log.

**Architecture:** A new `property` package (entities, repository, controller) mirrors the existing `user` package's shape. Image processing is a separate pure-logic component (`ImagePipelineService`, no Spring/DB dependencies) that the controller calls synchronously during the upload request; a new `storage` package wraps MinIO behind the AWS S3 SDK. The frontend adds a `/properties` section following the same page/lib/component split as `/users`.

**Tech Stack:** Spring Boot / Spring Data JPA / Flyway (existing), Thumbnailator + `org.sejda.imageio:webp-imageio` (new, image resize/encode), AWS SDK v2 `S3Client` against MinIO (new), Next.js / TypeScript / Jest (existing).

**Spec:** `docs/superpowers/specs/2026-11-24-crm-phase3-property-listings-design.md`

## Global Constraints

- No role restriction: any authenticated user may create, edit, and withdraw a property (design §2).
- Image processing is synchronous within the upload request — no background job (design §2).
- A property's `ca` (Catalan) translation is created atomically with the property itself, in one request (design §2).
- `property_media.variants` stores direct public URLs, not presigned ones (design §2).
- A failed bulk-upload request deletes any objects it already wrote to MinIO before returning its error (design §2).
- `currency` is fixed to `EUR`; no currency picker (design §2).
- `property.owner_contact_id` does not exist yet — no FK to `contact`, no "owner" field anywhere in this phase (design §2).
- This phase's media types are `PHOTO` and `FLOORPLAN` only; `VIDEO`/`VIRTUAL_TOUR` are rejected (design §2, §8).
- No multi-agency scoping — same deferred decision as Phase 2's `UserController` (design §2).

## Review Focus

- A non-image or corrupt file sent to the bulk-media endpoint must return 400, not a 500 or a silently broken `property_media` row (Task 4).
- A `mediaType` of `VIDEO` or `VIRTUAL_TOUR` sent to the bulk-media endpoint must return 400 rather than being run through the image pipeline (Task 4).
- `price` missing, zero, or negative must be rejected on create and update, not stored (Task 3).
- A bulk upload beyond a sane limit (more than 30 files, or any file over 20MB) must be rejected up front rather than hanging the synchronous request or exhausting memory (Task 4).
- Reordering media with a list that doesn't exactly match the property's current media ids (missing one, duplicating one, including a stray id) must be rejected rather than silently producing duplicate or missing `sort_order` values (Task 4).

---

### Task 1: Image pipeline core (resize + optional WebP encoding)

Pure image-processing logic — no Spring context, no database, no MinIO. This is also where the open question from the design doc (does WebP encoding actually work on the machine running the tests?) gets answered, as a permanent test rather than a throwaway spike.

**Files:**
- Modify: `backend/pom.xml` (add dependencies)
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/ImagePipelineService.java`
- Test: `backend/src/test/java/com/sweethomecompany/crm/property/ImagePipelineServiceTest.java`
- Test fixture: `backend/src/test/resources/test-photo.jpg` (any real small JPEG photo, landscape orientation, at least 2000px on the longest edge — needed so all four output sizes are genuinely downscaled, not upscaled)

**Interfaces:**
- Produces: `ImagePipelineService.Variant` record (`String size`, `String format`, `byte[] bytes`), `ImagePipelineService.process(byte[] originalImageBytes) -> List<Variant>`, `ImagePipelineService.isWebpAvailable() -> boolean`. Task 4 consumes all three.

- [ ] **Step 1: Add dependencies to `backend/pom.xml`**

Add `net.coobird:thumbnailator` (latest stable version from Maven Central), `org.sejda.imageio:webp-imageio` (latest stable version from Maven Central — bundles its own native library, no separate OS install needed per the design doc's research), and `software.amazon.awssdk:s3` (latest stable 2.x version from Maven Central — needed by Task 2, adding it now avoids a second pom edit).

- [ ] **Step 2: Write the failing tests**

```java
class ImagePipelineServiceTest {
    @Test
    void producesAllFourSizesInJpegAtMinimum() throws IOException {
        byte[] original = Files.readAllBytes(Path.of("src/test/resources/test-photo.jpg"));
        List<ImagePipelineService.Variant> variants = new ImagePipelineService().process(original);

        assertThat(variants).extracting(ImagePipelineService.Variant::size)
            .containsExactlyInAnyOrder("thumbnail", "card", "gallery", "full_detail", "thumbnail", "card", "gallery", "full_detail");
        // (the above is illustrative of the four sizes appearing; assert precisely that
        // for format "jpeg" all four of thumbnail/card/gallery/full_detail are present)

        Variant thumbnailJpeg = variantFor(variants, "thumbnail", "jpeg");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnailJpeg.bytes()));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isLessThanOrEqualTo(400);
    }

    @Test
    void includesWebpVariantsOnlyWhenWebpIsAvailableOnThisPlatform() throws IOException {
        byte[] original = Files.readAllBytes(Path.of("src/test/resources/test-photo.jpg"));
        ImagePipelineService service = new ImagePipelineService();
        List<ImagePipelineService.Variant> variants = service.process(original);

        boolean hasWebp = variants.stream().anyMatch(v -> v.format().equals("webp"));
        assertThat(hasWebp).isEqualTo(service.isWebpAvailable());
        if (service.isWebpAvailable()) {
            Variant thumbnailWebp = variantFor(variants, "thumbnail", "webp");
            assertThat(ImageIO.read(new ByteArrayInputStream(thumbnailWebp.bytes()))).isNotNull();
        }
    }

    @Test
    void rejectsBytesThatAreNotADecodableImage() {
        assertThatThrownBy(() -> new ImagePipelineService().process("not an image".getBytes()))
            .isInstanceOf(IOException.class);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -Dtest=ImagePipelineServiceTest`
Expected: FAIL — `ImagePipelineService` doesn't exist yet.

- [ ] **Step 4: Implement `ImagePipelineService` in `backend/src/main/java/com/sweethomecompany/crm/property/ImagePipelineService.java`**

Sizes (longest edge, aspect ratio preserved): `thumbnail` = 400, `card` = 800, `gallery` = 1600, `full_detail` = 3000. Use Thumbnailator to resize (it reads EXIF orientation and rotates automatically — no separate auto-orient step needed) and decode into a `BufferedImage`; re-encoding that `BufferedImage` with `ImageIO.write` naturally drops the original EXIF/GPS metadata, so no explicit "strip EXIF" step is needed either. Always produce a `jpeg` variant for each size. For `isWebpAvailable()`: on construction (or lazily, cached), attempt to encode a tiny in-memory image via `ImageIO.write(..., "webp", ...)` inside a try/catch; return `true` only if that succeeds and the written bytes are non-empty — this is what makes the test in Step 2 double as the Task-1 spike. When available, also produce a `webp` variant for each size. On input that `ImageIO.read` can't decode, throw `IOException`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=ImagePipelineServiceTest`
Expected: PASS. Note which way `isWebpAvailable()` came out on this machine — it doesn't change any later task's code either way.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/sweethomecompany/crm/property/ImagePipelineService.java backend/src/test/java/com/sweethomecompany/crm/property/ImagePipelineServiceTest.java backend/src/test/resources/test-photo.jpg
git commit -m "Add image resize/encode pipeline with runtime WebP capability detection"
```

---

### Task 2: Object storage (MinIO via the AWS S3 SDK)

**Files:**
- Modify: `docker-compose.yml` (add `minio` service)
- Modify: `backend/src/main/resources/application.yml` (add storage config)
- Create: `backend/src/main/java/com/sweethomecompany/crm/storage/StorageProperties.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/storage/S3Config.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/storage/ObjectStorageService.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/storage/MinioBucketInitializer.java`
- Test: `backend/src/test/java/com/sweethomecompany/crm/storage/ObjectStorageServiceTest.java`

**Interfaces:**
- Produces: `ObjectStorageService.upload(String key, byte[] content, String contentType) -> String` (returns the public URL), `ObjectStorageService.delete(String key)`. Task 4 consumes both.

- [ ] **Step 1: Add the `minio` service to `docker-compose.yml`**

```yaml
  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: crm
      MINIO_ROOT_PASSWORD: crm_dev_only
    ports:
      - "9000:9000"
      - "9001:9001"
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 3s
      retries: 5
```

- [ ] **Step 2: Add storage config to `backend/src/main/resources/application.yml`**

```yaml
storage:
  s3:
    endpoint: http://localhost:9000
    bucket: crm-media
    access-key: ${MINIO_ROOT_USER:crm}
    secret-key: ${MINIO_ROOT_PASSWORD:crm_dev_only}
    public-base-url: http://localhost:9000/crm-media
```

- [ ] **Step 3: Write the failing test**

```java
// Runs against the real local MinIO from docker-compose.yml, same pattern
// the project already uses for Postgres — no Testcontainers.
class ObjectStorageServiceTest {
    @Test
    void uploadReturnsAPubliclyFetchableUrlAndDeleteRemovesIt() throws Exception {
        ObjectStorageService service = /* constructed with the real S3Client bean */;
        String key = "test/" + UUID.randomUUID() + ".txt";

        String url = service.upload(key, "hello".getBytes(), "text/plain");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello");

        service.delete(key);
        HttpResponse<String> afterDelete = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(afterDelete.statusCode()).isEqualTo(404);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn test -Dtest=ObjectStorageServiceTest`
Expected: FAIL — `ObjectStorageService` doesn't exist yet.

- [ ] **Step 5: Implement**

`StorageProperties` — a `@ConfigurationProperties(prefix = "storage.s3")` record/class exposing `endpoint`, `bucket`, `accessKey`, `secretKey`, `publicBaseUrl`.

`S3Config` — declares an `S3Client` `@Bean` built from `StorageProperties`: `endpointOverride`, static credentials, path-style access enabled (required for MinIO), region can be any fixed placeholder (e.g. `us-east-1`) since MinIO ignores it.

`ObjectStorageService` — wraps the injected `S3Client`. `upload(key, content, contentType)` does a `PutObjectRequest` then returns `publicBaseUrl + "/" + key`. `delete(key)` does a `DeleteObjectRequest`.

`MinioBucketInitializer implements ApplicationRunner` — on every startup, checks whether the configured bucket exists (`HeadBucketRequest`); if not, creates it and applies a public-read bucket policy (anonymous `s3:GetObject` on `arn:aws:s3:::{bucket}/*`). Same idempotent-on-every-startup shape as `AdminUserSeeder`.

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=ObjectStorageServiceTest`
Expected: PASS (requires `docker compose up -d minio` running locally first).

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml backend/src/main/resources/application.yml backend/src/main/java/com/sweethomecompany/crm/storage/ backend/src/test/java/com/sweethomecompany/crm/storage/
git commit -m "Add MinIO object storage via AWS S3 SDK with idempotent bucket setup"
```

---

### Task 3: Property entity, migration, and core CRUD endpoints

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_property_tables.sql`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/Property.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyTranslation.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyRepository.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyTranslationRepository.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/CreatePropertyRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/UpdatePropertyRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/AddTranslationRequest.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyResponse.java` (includes nested `TranslationResponse`, and an empty `media` list for now — Task 4 populates it)
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertySummaryResponse.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyController.java`
- Test: `backend/src/test/java/com/sweethomecompany/crm/property/PropertyControllerTest.java`

**Interfaces:**
- Consumes: `AuditLogService.log(...)` (Phase 2, `com.sweethomecompany.crm.audit`), the authenticated `AppUserPrincipal` from the security context (Phase 2, same pattern `UserController` already uses for `actor`).
- Produces: `Property` JPA entity, `PropertyRepository extends JpaRepository<Property, UUID>` with `boolean existsByAgencyIdAndReferenceCode(UUID agencyId, String referenceCode)`, `PropertyResponse`/`PropertySummaryResponse`. Task 4 adds a `media` field onto `PropertyResponse`'s construction and extends `PropertyController`.

- [ ] **Step 1: Write the migration**

Create `V3__create_property_tables.sql` with exactly the three `CREATE TABLE` statements (and the two indexes) from design doc §3 — `property` (no `owner_contact_id` column), `property_translation`, `property_media` (this task doesn't use `property_media` yet, but creating it now avoids a second migration later; Task 4 is the first to read/write it).

- [ ] **Step 2: Write the failing tests**

```java
class PropertyControllerTest {
    @Test
    void creatingAPropertyWithValidDataSucceedsAndWritesACreateAuditRow() { /* POST /api/properties with all required fields incl. caTitle; assert 201, body has id, reference code, status DRAFT, translations == [{locale: "ca", title: ..., description: ...}]; assert one AuditLog row with action=CREATE, entityType="PROPERTY" */ }

    @Test
    void creatingWithADuplicateReferenceCodeInTheSameAgencyReturns409() { }

    @Test
    void creatingWithoutACatalanTitleReturns400() { }

    @Test
    void creatingWithAZeroOrNegativePriceReturns400() { } // Review Focus

    @Test
    void gettingAnExistingPropertyReturnsItWithItsCaTranslation() { }

    @Test
    void gettingANonexistentPropertyReturns404() { }

    @Test
    void listingReturnsSummariesForAllProperties() { }

    @Test
    void updatingFieldsIncludingStatusSucceedsAndWritesAnUpdateAuditRowWithChangedFields() { } // this is how "withdraw" happens: status -> WITHDRAWN

    @Test
    void updatingTheCatalanTitleThroughPatchChangesTheCaTranslation() { }

    @Test
    void addingANonCatalanTranslationSucceeds() { }

    @Test
    void addingATranslationWithLocaleCaIsRejectedWith400() { } // ca only changes via PATCH, not this endpoint
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -Dtest=PropertyControllerTest`
Expected: FAIL — none of these types/endpoints exist yet.

- [ ] **Step 4: Implement the entities, repositories, DTOs, and controller**

`Property` — JPA entity matching the migration's columns one-for-one (including `agencyId`, `createdByUserId`); `status` defaults to `"DRAFT"` when not supplied by the client.

`PropertyTranslation` — JPA entity: `id`, `propertyId`, `locale`, `title`, `description`.

`CreatePropertyRequest` — a record with the fields listed in design doc §3 (minus `id`/`agencyId`/`status`/`createdAt`/`updatedAt`/`currency`/`country`) plus `caTitle` and `caDescription`. Required (reject with 400 if blank/null): `referenceCode`, `propertyType`, `operation`, `price`, `caTitle`.

`UpdatePropertyRequest` — same shape as `CreatePropertyRequest` plus `status`, every field nullable — only non-null fields are applied, same PATCH semantics `UpdateUserRequest` already established in Phase 2.

`AddTranslationRequest(String locale, String title, String description)` — `locale` must be one of `es`/`fr`/`en` (400 if `ca` or anything else), `title` required.

`PropertyController`:
- `POST /api/properties` — validates required fields and `price > 0`; checks `existsByAgencyIdAndReferenceCode` for the 409; inserts `Property` and its `ca` `PropertyTranslation` in one transaction (`@Transactional`); logs `CREATE`.
- `GET /api/properties` — maps all properties (current agency, per the no-scoping constraint) to `PropertySummaryResponse`; `thumbnailUrl` is `null` until Task 4 adds media.
- `GET /api/properties/{id}` — 404 if missing, else full `PropertyResponse` with its translations.
- `PATCH /api/properties/{id}` — applies non-null fields, updates the `ca` translation's title/description if `caTitle`/`caDescription` were sent, builds a changed-fields map the same way `UserController.update()` already does, logs `UPDATE` only if anything changed.
- `POST /api/properties/{id}/translations` — upserts the translation for the given non-`ca` locale.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=PropertyControllerTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V3__create_property_tables.sql backend/src/main/java/com/sweethomecompany/crm/property/ backend/src/test/java/com/sweethomecompany/crm/property/PropertyControllerTest.java
git commit -m "Add property entity, migration, and core CRUD + translation endpoints"
```

---

### Task 4: Bulk media upload, delete, and reorder

**Files:**
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyMedia.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyMediaRepository.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/MediaResponse.java`
- Create: `backend/src/main/java/com/sweethomecompany/crm/property/MediaOrderRequest.java`
- Modify: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyController.java` (add the three media endpoints)
- Modify: `backend/src/main/java/com/sweethomecompany/crm/property/PropertyResponse.java` (populate `media` from `PropertyMediaRepository`)
- Test: `backend/src/test/java/com/sweethomecompany/crm/property/PropertyMediaControllerTest.java`

**Interfaces:**
- Consumes: `ImagePipelineService.process`/`isWebpAvailable` (Task 1), `ObjectStorageService.upload`/`delete` (Task 2), `Property`/`PropertyRepository` (Task 3).
- Produces: `PropertyMediaRepository extends JpaRepository<PropertyMedia, UUID>` with `List<PropertyMedia> findByPropertyIdOrderBySortOrder(UUID propertyId)`.

- [ ] **Step 1: Write the failing tests**

```java
class PropertyMediaControllerTest {
    @Test
    void uploadingRealPhotosCreatesOneMediaRowPerPhotoWithAllExpectedVariantKeys() {
        // POST multipart to /api/properties/{id}/media with mediaType=PHOTO and 2 real
        // small JPEG files; assert 201, 2 MediaResponse rows, each variants map has
        // thumbnail/card/gallery/full_detail keys for "jpeg" (and for "webp" iff
        // ImagePipelineService.isWebpAvailable() on this machine); fetch one variant
        // URL over HTTP and assert 200. Test cleans up the MinIO objects it created.
    }

    @Test
    void uploadingANonImageFileReturns400AndWritesNothing() { } // Review Focus

    @Test
    void uploadingWithMediaTypeVideoReturns400() { } // Review Focus — out of scope this phase

    @Test
    void uploadingMoreThanThirtyFilesInOneRequestReturns400() { } // Review Focus

    @Test
    void whenOneFileInABatchFailsNoMediaRowsAreCreatedAndAnyAlreadyUploadedObjectIsDeleted() {
        // batch of [valid.jpg, corrupt.jpg]; assert 400, zero PropertyMedia rows for
        // this property, and that valid.jpg's variant URLs now 404 (cleaned up)
    }

    @Test
    void deletingAMediaRowRemovesItAndItsUnderlyingObjects() { }

    @Test
    void reorderingWithTheFullCurrentSetOfMediaIdsUpdatesSortOrderToMatch() { }

    @Test
    void reorderingWithAMissingOrUnknownMediaIdReturns400() { } // Review Focus
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=PropertyMediaControllerTest`
Expected: FAIL — these endpoints don't exist yet.

- [ ] **Step 3: Implement**

`PropertyMedia` — JPA entity matching the migration: `id`, `propertyId`, `mediaType`, `variants` (stored as `jsonb`; same JSON-column approach the project already has available via the injected `ObjectMapper`, serializing a `Map<String,String>`), `sortOrder`, `uploadedAt`.

Add to `PropertyController` (or extract into a sibling controller/service if the file is getting large — your call):
- `POST /api/properties/{id}/media` — multipart, a `mediaType` request param restricted to `PHOTO`/`FLOORPLAN` (400 otherwise), and 1-30 files (400 if more), each ≤ 20MB (400 otherwise). For each file in order: call `ImagePipelineService.process`, catching `IOException` as "not a decodable image" → 400. If every file processes cleanly, upload every variant of every file via `ObjectStorageService.upload` (key shape: `properties/{propertyId}/{mediaId}/{size}_{format}.{ext}`) and insert one `PropertyMedia` row per file with `sortOrder` continuing after the property's existing max. If any file fails partway, call `ObjectStorageService.delete` for every object already uploaded in this request, insert nothing, and return 400 naming the failing file.
- `PATCH /api/properties/{id}/media/order` — body is `MediaOrderRequest(List<UUID> mediaIdsInOrder)`; 400 unless the list is exactly the set of the property's current media ids (same size, no duplicates, no unknown ids); otherwise sets each row's `sortOrder` to its index in the list.
- `DELETE /api/properties/{id}/media/{mediaId}` — deletes the underlying MinIO objects (every URL in `variants`) then the row; 204.

Update `PropertyResponse`'s construction in `PropertyController`'s `GET` endpoints to include `media` via `PropertyMediaRepository.findByPropertyIdOrderBySortOrder`, and update the `GET /api/properties` summary mapping to set `thumbnailUrl` from the first media item's `thumbnail_jpeg` (or `thumbnail_webp` if that's what's present) variant, `null` if the property has no media yet.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=PropertyMediaControllerTest`
Expected: PASS (requires local MinIO running)

- [ ] **Step 5: Run the full backend suite**

Run: `mvn test`
Expected: PASS — confirms Tasks 1-4 together, plus Phase 1/2's existing tests, are all still green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sweethomecompany/crm/property/ backend/src/test/java/com/sweethomecompany/crm/property/PropertyMediaControllerTest.java
git commit -m "Add bulk media upload, delete, and reorder endpoints with failure cleanup"
```

---

### Task 5: Frontend — properties list page

**Files:**
- Create: `frontend/lib/properties.ts`
- Create: `frontend/app/properties/page.tsx`
- Test: `frontend/app/properties/page.test.tsx`
- Modify: `frontend/app/components/NavBar.tsx` (add a "Listings" link to `/properties`, visible to every logged-in user)
- Modify: `frontend/app/components/NavBar.test.tsx`

**Interfaces:**
- Produces: `fetchProperties(): Promise<PropertySummary[]>` in `frontend/lib/properties.ts`, where `PropertySummary` mirrors the backend's `PropertySummaryResponse` fields (`id`, `referenceCode`, `propertyType`, `operation`, `status`, `price`, `thumbnailUrl`). Tasks 6-8 extend `properties.ts` with more functions and reuse this type.

- [ ] **Step 1: Write the failing tests**

```tsx
// NavBar.test.tsx: add one case
it("shows a Listings link for any logged-in user", () => { /* render with a MANAGER or AGENT user; expect link to /properties */ });
```

```tsx
// properties/page.test.tsx
describe("Properties list page", () => {
  it("renders each property's reference code, status, price, and thumbnail", () => { /* mock fetchProperties, assert rendered rows */ });
  it("shows a New listing link to /properties/new", () => { });
  it("shows an empty state when there are no properties yet", () => { });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- properties NavBar`
Expected: FAIL

- [ ] **Step 3: Implement `frontend/lib/properties.ts` and `frontend/app/properties/page.tsx`**

`fetchProperties()` — `GET /api/properties` with `credentials: "include"`, returns the parsed JSON array.

`page.tsx` — server component (matching the existing `/users` page pattern): fetch the list, render a table (reference code / type+operation / status / price / thumbnail image), a "New listing" link to `/properties/new`, and an empty-state message when the array is empty.

Add the "Listings" `<Link href="/properties">` to `NavBar.tsx` unconditionally (no role check, per the Global Constraints).

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- properties NavBar`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/properties.ts frontend/app/properties/page.tsx frontend/app/properties/page.test.tsx frontend/app/components/NavBar.tsx frontend/app/components/NavBar.test.tsx
git commit -m "Add properties list page and nav link"
```

---

### Task 6: Frontend — create listing form

Creates the property's text fields only; photos are added afterward on the edit page (`POST /api/properties/{id}/media` needs an id that doesn't exist until the property is created).

**Files:**
- Create: `frontend/app/properties/new/page.tsx`
- Test: `frontend/app/properties/new/page.test.tsx`
- Modify: `frontend/lib/properties.ts` (add `createProperty`)

**Interfaces:**
- Consumes: `PropertySummary` type (Task 5).
- Produces: `createProperty(data: CreatePropertyInput): Promise<{ id: string }>` in `frontend/lib/properties.ts`, where `CreatePropertyInput` mirrors the backend's `CreatePropertyRequest` fields. Task 8's edit page reuses the same field set for its form.

- [ ] **Step 1: Write the failing tests**

```tsx
describe("New listing page", () => {
  it("submits all entered fields and redirects to the edit page for the new property", () => { /* mock createProperty resolving {id: "abc"}; assert router.push("/properties/abc/edit") */ });
  it("shows a validation error when reference code, price, or the Catalan title is missing", () => { });
  it("shows the server's error message when the reference code is already taken", () => { /* mock createProperty rejecting with a 409 */ });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- properties/new`
Expected: FAIL

- [ ] **Step 3: Implement**

`createProperty` in `properties.ts` — `POST /api/properties` with the CSRF header, same pattern `login/page.tsx` already uses.

`new/page.tsx` — a client component form with inputs for every `CreatePropertyRequest` field (property type and operation as `<select>`s over their enum values from design doc §3; `caTitle`/`caDescription` labeled as the listing's Catalan title/description); client-side required-field check mirroring the backend's required set (`referenceCode`, `propertyType`, `operation`, `price`, `caTitle`) before submitting; on success, `router.push`'s to `/properties/{id}/edit`; on failure, shows the error inline.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- properties/new`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/properties.ts frontend/app/properties/new/
git commit -m "Add create-listing form"
```

---

### Task 7: Frontend — listing detail page

**Files:**
- Create: `frontend/app/properties/[id]/page.tsx`
- Test: `frontend/app/properties/[id]/page.test.tsx`
- Modify: `frontend/lib/properties.ts` (add `fetchProperty`)

**Interfaces:**
- Produces: `fetchProperty(id: string): Promise<PropertyDetail>` where `PropertyDetail` mirrors the backend's `PropertyResponse` (all fields, `translations: Translation[]`, `media: Media[]`). Task 8's edit page reuses this type.

- [ ] **Step 1: Write the failing tests**

```tsx
describe("Property detail page", () => {
  it("renders every field, the Catalan translation, and a photo gallery from the media variants", () => { });
  it("shows an Edit link to /properties/{id}/edit", () => { });
  it("lists any additional translations already added", () => { });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- "properties/\[id\]/page"`
Expected: FAIL

- [ ] **Step 3: Implement**

`fetchProperty` in `properties.ts` — `GET /api/properties/{id}`.

`[id]/page.tsx` — server component: full field display, a gallery using each media item's `card` (or `gallery`) variant URL, the Catalan title/description plus any other translations present, and an "Edit" link to `/properties/{id}/edit`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- "properties/\[id\]/page"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/properties.ts "frontend/app/properties/[id]/page.tsx" "frontend/app/properties/[id]/page.test.tsx"
git commit -m "Add listing detail page"
```

---

### Task 8: Frontend — edit form, photo uploader, and translations

**Files:**
- Create: `frontend/app/components/PhotoUploader.tsx`
- Test: `frontend/app/components/PhotoUploader.test.tsx`
- Create: `frontend/app/properties/[id]/edit/page.tsx`
- Test: `frontend/app/properties/[id]/edit/page.test.tsx`
- Modify: `frontend/lib/properties.ts` (add `updateProperty`, `uploadMedia`, `deleteMedia`, `reorderMedia`, `addTranslation`)

**Interfaces:**
- Consumes: `PropertyDetail`, `CreatePropertyInput`-shaped fields (Tasks 6-7).
- Produces: `PhotoUploader` component with props `{ propertyId: string, media: Media[] }`, calling `uploadMedia`/`deleteMedia`/`reorderMedia` internally and refreshing its list on success.

- [ ] **Step 1: Write the failing tests**

```tsx
describe("PhotoUploader", () => {
  it("uploads the selected files and shows the new thumbnails", () => { });
  it("shows the server's error message when upload fails (e.g. a non-image file)", () => { });
  it("removes a photo when its delete button is clicked", () => { });
  it("reorders photos via drag-and-drop and persists the new order", () => { });
});

describe("Edit listing page", () => {
  it("loads the property's current values into the form", () => { });
  it("submits only the changed fields and shows a saved confirmation", () => { });
  it("includes WITHDRAWN as a status option and setting it saves successfully", () => { });
  it("lets an additional (non-Catalan) translation be added and saved", () => { });
  it("renders the PhotoUploader with the property's existing media", () => { });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- PhotoUploader "properties/\[id\]/edit"`
Expected: FAIL

- [ ] **Step 3: Implement**

Add to `properties.ts`: `updateProperty(id, data)` (PATCH), `uploadMedia(propertyId, mediaType, files)` (multipart POST), `deleteMedia(propertyId, mediaId)` (DELETE), `reorderMedia(propertyId, mediaIdsInOrder)` (PATCH), `addTranslation(propertyId, locale, title, description)` (POST) — all with the CSRF header and `credentials: "include"`.

`PhotoUploader.tsx` — a multi-file `<input type="file" multiple>`, a thumbnail grid (using each item's `thumbnail` variant) with a delete button per photo and drag-and-drop reordering that calls `reorderMedia` with the new full id order on drop.

`[id]/edit/page.tsx` — same field set as the create form (Task 6), pre-filled from `fetchProperty`; a `status` `<select>` including `WITHDRAWN`; a small form to add one more translation (locale `<select>` restricted to `es`/`fr`/`en`, title, description); renders `<PhotoUploader propertyId={id} media={property.media} />`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- PhotoUploader "properties/\[id\]/edit"`
Expected: PASS

- [ ] **Step 5: Run the full frontend suite**

Run: `npm test`
Expected: PASS — confirms Tasks 5-8 together, plus Phases 1-2's existing tests, are all still green.

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/properties.ts frontend/app/components/PhotoUploader.tsx frontend/app/components/PhotoUploader.test.tsx "frontend/app/properties/[id]/edit/"
git commit -m "Add edit-listing form with photo upload, reordering, and translations"
```

---

### Task 9: Manual full-stack verification

No new code — this is the end-to-end human check, same spirit as Phase 1's and Phase 2's final manual pass.

- [ ] **Step 1:** Run `docker compose up -d` (now starts Postgres *and* MinIO), start the backend and frontend.
- [ ] **Step 2:** Log in, go to Listings, create a new property with a handful of real photos, and confirm it appears in the list with a thumbnail.
- [ ] **Step 3:** Open its detail page, confirm every field and all photos render at reasonable quality.
- [ ] **Step 4:** Edit a field, add a second (non-Catalan) translation, reorder the photos, delete one photo — confirm each change persists after a reload.
- [ ] **Step 5:** Set status to `WITHDRAWN` and confirm it's reflected in both the detail page and the list.
- [ ] **Step 6:** Report back: what you saw for `ImagePipelineService.isWebpAvailable()` on this machine (JPEG-only is fine, just confirm which), and confirm both full test suites are still green.

---

## Self-Review Notes

**Spec coverage:** Every section of the design doc maps to a task — §3 migration → Task 3; §4 pipeline → Tasks 1, 4; §5 backend components → Tasks 2-4; §6 frontend → Tasks 5-8; §7 testing → woven into each task plus Task 9's manual pass. §8's exclusions (owner_contact_id, video/virtual tour, role gating, multi-currency, map display) are named in Global Constraints so no task accidentally builds them.

**Type consistency:** `PropertySummaryResponse`/`PropertyResponse`/`MediaResponse` (Task 3-4, backend) line up with `PropertySummary`/`PropertyDetail`/`Media` (Tasks 5-8, frontend) field-for-field. `ImagePipelineService.Variant` (Task 1) is consumed only by Task 4, which is the only place `size`/`format` string values need to match the `variants` map keys described in design doc §4.

**Review Focus:** all five lines have an owning task and test, listed above.
