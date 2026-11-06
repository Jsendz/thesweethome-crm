# CRM Phase 1 — Project Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a runnable Spring Boot backend and Next.js admin frontend, both talking to a locally-dockerized Postgres, with no CRM features yet — just the foundation that every later phase builds on.

**Architecture:** A single repository with `backend/` (Spring Boot, Maven) and `frontend/` (Next.js, App Router, TypeScript) as sibling directories, plus a root `docker-compose.yml` that runs Postgres for local development. The backend exposes Spring Boot Actuator's health endpoint (extended to report database connectivity once Postgres is wired in); the frontend fetches it and displays backend/database status, proving the full chain works end to end before any domain code is written.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Maven, Spring Boot Actuator, Spring Data JPA, PostgreSQL JDBC driver, Flyway. Next.js (App Router) + TypeScript, Jest + React Testing Library. Docker Compose for local Postgres (the database-aware backend test runs against this same Postgres, started first — no Testcontainers dependency).

**Spec:** `docs/superpowers/specs/2026-11-06-crm-foundation-design.md` (Real Estate CRM — Foundation Design), section 7, Phase 1.

**Scope note:** The spec's Phase 1 through Phase 9 are nine sequential build phases, not independent subsystems — each depends on the previous one's foundation (e.g. Phase 3's listings need Phase 2's auth and audit log). Per the scope check for this skill, each phase gets its own plan, produced and reviewed once the prior phase is implemented, rather than one plan covering all nine. This document covers **Phase 1 only**.

## Global Constraints

- Backend is Java/Spring Boot, not Kotlin or another JVM language — this is a deliberate choice tied to the project's purpose (spec section 1), not just a default.
- No paid or managed services anywhere in this phase — Postgres runs self-hosted via Docker Compose, locally now and on the target VM later (spec section 3).
- Every table in the eventual schema carries an `agency_id` column (spec section 4) — not yet relevant since Phase 1 defines no tables, but no Phase 1 code should assume a single implicit tenant in a way that would need rework.
- Database credentials come from environment variables with safe local-only defaults; no production-style secret is ever hardcoded or committed (spec section 1, compliance constraints).

## Review Focus

- Postgres not yet reachable when the backend starts — for local dev this is sidestepped by always starting Postgres before the backend (Task 4's manual verification order) and by the Postgres healthcheck added to `docker-compose.yml` in Task 2, which Phase 9's production Compose file will use with `depends_on: condition: service_healthy` once the backend itself is containerized.
- A cross-origin request from the Next.js dev server (port 3000) to the Spring Boot dev server (port 8080) must succeed, not silently fail with a browser CORS error (Task 4, tested).
- The frontend must show a clear "unreachable" state when the backend is down or errors, not crash or render blank (Task 3, tested).
- Database credentials must come from environment variables with safe local-only defaults, never a hardcoded production value committed to the repo (Task 2, enforced by code review against the Global Constraints above — no automated test can verify a negative like "nothing is hardcoded").
- A Flyway migration failure must stop the app from starting rather than let it run against a half-migrated schema — this is Flyway's own default behavior, not something Phase 1 needs to build, but it's worth confirming once real migrations exist starting in Phase 2.

---

## Task 1: Backend skeleton with Actuator health endpoint

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/sweethomecompany/crm/CrmApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/sweethomecompany/crm/CrmApplicationHealthTest.java`

**Interfaces:**
- Produces: `GET /actuator/health` → `200 {"status":"UP"}` (no database component yet — added in Task 2). Every later phase's own health/readiness checks build on this same endpoint rather than inventing a new one.

- [ ] **Step 1: Write the failing test**

```java
package com.sweethomecompany.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrmApplicationHealthTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CrmApplicationHealthTest`
Expected: FAIL (no Maven project / main application class exists yet)

- [ ] **Step 3: Create the Maven project and application class**

`backend/pom.xml`: Spring Boot 3.3.x parent, Java 21, dependencies `spring-boot-starter-web`, `spring-boot-starter-actuator`, `spring-boot-starter-test` (test scope), `assertj-core` (brought in transitively by starter-test — do not add separately).

`backend/src/main/java/com/sweethomecompany/crm/CrmApplication.java`:
```java
@SpringBootApplication
public class CrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
```

`backend/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: crm-backend
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CrmApplicationHealthTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src
git commit -m "Scaffold Spring Boot backend with Actuator health endpoint"
```

---

## Task 2: Postgres via Docker Compose, Flyway, and a database-aware health check

**Files:**
- Create: `docker-compose.yml`
- Modify: `backend/pom.xml` (add `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`)
- Modify: `backend/src/main/resources/application.yml` (add `spring.datasource.*` reading from env vars with local defaults)
- Test: `backend/src/test/java/com/sweethomecompany/crm/CrmApplicationDbHealthTest.java`

**Interfaces:**
- Consumes: nothing new from Task 1 beyond the running `CrmApplication` context. Assumes Postgres is already reachable at the configured `DB_HOST`/`DB_PORT` (started via `docker compose up -d postgres`) — this is a plain integration test against a real, already-running Postgres, not a self-contained Testcontainers test, so the same Postgres you'll run day-to-day via Compose is what the test exercises.
- Produces: `GET /actuator/health` now also returns a `components.db.status` field once a `DataSource` bean exists — later phases' entities and repositories rely on this same `DataSource`/Flyway wiring, not a new one.

- [ ] **Step 1: Write the failing test**

```java
package com.sweethomecompany.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// Requires Postgres reachable at the configured DB_HOST/DB_PORT — run
// `docker compose up -d postgres` first.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrmApplicationDbHealthTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsDatabaseUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getBody()).contains("\"db\"").contains("\"status\":\"UP\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CrmApplicationDbHealthTest`
Expected: FAIL (no "db" component in the health response yet — the datasource dependency isn't on the classpath, so this fails the same way whether or not Postgres happens to be running)

- [ ] **Step 3: Add dependencies and datasource config**

Add the dependencies listed above to `backend/pom.xml`.

`backend/src/main/resources/application.yml` additions:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:crm}
    username: ${DB_USER:crm}
    password: ${DB_PASSWORD:crm_dev_only}
  flyway:
    enabled: true
```

`docker-compose.yml`:
```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: crm
      POSTGRES_USER: crm
      POSTGRES_PASSWORD: crm_dev_only
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U crm"]
      interval: 5s
      timeout: 3s
      retries: 5
```

No Flyway migration files are added in this task — Flyway is valid and runs cleanly with zero migrations; the first real migration (the `agency`/`app_user` tables) belongs to Phase 2.

- [ ] **Step 4: Run test to verify it passes**

Run: `docker compose up -d postgres` (wait until `docker compose ps` shows it healthy), then `cd backend && ./mvnw test -Dtest=CrmApplicationDbHealthTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml backend/pom.xml backend/src
git commit -m "Wire Postgres, Flyway, and database-aware health check"
```

---

## Task 3: Frontend skeleton showing backend/database status

**Files:**
- Create: `frontend/` (scaffolded via `npx create-next-app@latest frontend --typescript --app --no-tailwind --eslint`)
- Create: `frontend/lib/health.ts`
- Create: `frontend/app/page.tsx`
- Test: `frontend/lib/health.test.ts` (Jest)
- Test: `frontend/app/page.test.tsx` (Jest + React Testing Library)

**Interfaces:**
- Consumes: `GET /actuator/health` as produced by Task 2 — specifically the shape `{"status": "UP"|"DOWN", "components": {"db": {"status": "UP"|"DOWN"}}}`.
- Produces: `fetchHealth(): Promise<HealthStatus>` in `frontend/lib/health.ts`, where `HealthStatus = { backend: "UP" | "DOWN"; database: "UP" | "DOWN" | "UNKNOWN" }` — later phases' pages reuse this function and type rather than re-fetching `/actuator/health` themselves.

- [ ] **Step 1: Write the failing test for `fetchHealth`**

```typescript
import { fetchHealth } from "./health";

describe("fetchHealth", () => {
  it("returns UP/UP when the backend reports a healthy database", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: "UP", components: { db: { status: "UP" } } }),
    }) as jest.Mock;

    const result = await fetchHealth();
    expect(result).toEqual({ backend: "UP", database: "UP" });
  });

  it("returns backend DOWN and database UNKNOWN when the fetch itself fails", async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error("network error"));

    const result = await fetchHealth();
    expect(result).toEqual({ backend: "DOWN", database: "UNKNOWN" });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- health.test.ts`
Expected: FAIL (`frontend/lib/health.ts` does not exist)

- [ ] **Step 3: Implement `fetchHealth(): Promise<HealthStatus>` in `frontend/lib/health.ts`**

Read `process.env.NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`) for the backend origin; catch fetch/network errors and return `{ backend: "DOWN", database: "UNKNOWN" }` rather than throwing.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- health.test.ts`
Expected: PASS

- [ ] **Step 5: Write the failing test for the status page**

```tsx
import { render, screen } from "@testing-library/react";
import Page from "./page";
import { fetchHealth } from "../lib/health";

jest.mock("../lib/health");

describe("Home page", () => {
  it("shows backend and database status once loaded", async () => {
    (fetchHealth as jest.Mock).mockResolvedValue({ backend: "UP", database: "UP" });
    render(await Page());
    expect(screen.getByText(/Backend: UP/i)).toBeInTheDocument();
    expect(screen.getByText(/Database: UP/i)).toBeInTheDocument();
  });

  it("shows an unreachable state when the backend cannot be reached", async () => {
    (fetchHealth as jest.Mock).mockResolvedValue({ backend: "DOWN", database: "UNKNOWN" });
    render(await Page());
    expect(screen.getByText(/Backend: DOWN/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd frontend && npm test -- page.test.tsx`
Expected: FAIL (`app/page.tsx` doesn't yet render status text)

- [ ] **Step 7: Implement `app/page.tsx` as an async Server Component**

Call `fetchHealth()`, render `Backend: {backend}` and `Database: {database}` as plain text — no styling requirements yet.

- [ ] **Step 8: Run test to verify it passes**

Run: `cd frontend && npm test -- page.test.tsx`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add frontend
git commit -m "Scaffold Next.js frontend showing backend/database health"
```

---

## Task 4: CORS wiring and full-stack local verification

**Files:**
- Create: `backend/src/main/java/com/sweethomecompany/crm/config/CorsConfig.java`
- Test: `backend/src/test/java/com/sweethomecompany/crm/config/CorsConfigTest.java`
- Modify: `README.md` (local dev quick-start steps)

**Interfaces:**
- Consumes: nothing new.
- Produces: CORS allowance for `http://localhost:3000` on all `/actuator/**` and future `/api/**` paths — every later phase's REST controllers are covered by this same config, not a per-controller one.

- [ ] **Step 1: Write the failing test**

```java
package com.sweethomecompany.crm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allowsRequestsFromLocalFrontendOrigin() {
        RequestEntity<Void> request = RequestEntity
            .get(URI.create("/actuator/health"))
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .build();

        ResponseEntity<String> response = restTemplate.exchange(request, String.class);

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .isEqualTo("http://localhost:3000");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CorsConfigTest`
Expected: FAIL (no CORS config exists; header absent)

- [ ] **Step 3: Implement `CorsConfig` as a `WebMvcConfigurer` bean**

Register a `CorsRegistry` mapping for `/actuator/**` and `/api/**` allowing origin `http://localhost:3000`, methods `GET,POST,PUT,PATCH,DELETE,OPTIONS`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CorsConfigTest`
Expected: PASS

- [ ] **Step 5: Manually verify the full stack locally**

Run: `docker compose up -d postgres`
Run: `cd backend && ./mvnw spring-boot:run` (separate terminal)
Run: `cd frontend && npm run dev` (separate terminal)
Open `http://localhost:3000` — expected: page shows `Backend: UP` and `Database: UP`.
Run: `docker compose stop postgres` — expected: reloading the page shows `Backend: UP` and `Database: DOWN` (backend itself is still up; only the DB component flips).

- [ ] **Step 6: Document the quick-start in `README.md`**

The four commands above, in order, plus the required local tooling (Java 21, Node, Docker).

- [ ] **Step 7: Commit**

```bash
git add backend/src README.md
git commit -m "Add CORS config and local full-stack quick-start"
```
