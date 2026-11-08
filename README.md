# The Sweet Home Company — CRM

Real estate CRM for The Sweet Home Company (Andorra). Phase 1 (project
skeleton): Spring Boot backend + Postgres, Next.js frontend, wired
together locally.

## Prerequisites

- Java 21 (Temurin recommended) and Maven, both on PATH (`java -version`,
  `mvn -v`)
- Node.js (LTS) and npm, on PATH (`node -v`, `npm -v`)
- Docker Desktop, running

## Local quick start

Run each of these in its own terminal, from the repo root, in order:

1. Start Postgres:
   ```
   docker compose up -d postgres
   ```
2. Start the backend (separate terminal). No port flags needed — it
   listens on http://localhost:8081 by default:
   ```
   cd backend
   mvn spring-boot:run
   ```
3. Start the frontend (separate terminal):
   ```
   cd frontend
   npm install
   npm run dev
   ```
4. Open http://localhost:3000 — it should show `Backend: UP` and
   `Database: UP`.

To see the database-down state, stop Postgres only:

```
docker compose stop postgres
```

Reload the page: `Backend: UP` stays and `Database: DOWN` appears (the
first reload can take ~30 s while the connection pool times out). Bring
it back with:

```
docker compose start postgres
```

## Running the tests

```
cd backend
mvn test            # needs Postgres running (step 1)
```

```
cd frontend
npm test
```

## Notes

- **Backend port is 8081, not 8080.** Native EDB/Postgres services on this
  machine (the PEM Apache server) are bound to 8080, so the backend defaults
  to 8081. Override with the `SERVER_PORT` env var; if you do, point the
  frontend at it with `NEXT_PUBLIC_API_BASE_URL` (it defaults to
  `http://localhost:8081`).
- **Postgres host port is 5435, not 5432**, because a native Postgres
  install already owns 5432. Override with `DB_PORT`.

| Service  | Host port | Override env var |
|----------|-----------|------------------|
| Postgres | 5435      | `DB_PORT` (backend side) |
| Backend  | 8081      | `SERVER_PORT` |
| Frontend | 3000      | — |
