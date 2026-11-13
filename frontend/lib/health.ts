import { API_BASE_URL } from "./apiBaseUrl";

export type HealthStatus = {
  backend: "UP" | "DOWN";
  database: "UP" | "DOWN" | "UNKNOWN";
};

export async function fetchHealth(): Promise<HealthStatus> {
  try {
    const response = await fetch(`${API_BASE_URL}/actuator/health`);
    const body = await response.json();
    // Actuator's top-level status aggregates components (a DOWN db makes it
    // DOWN + HTTP 503), so it can't tell us whether the backend itself is up.
    // Getting a health body back at all means the backend is reachable.
    const backend: HealthStatus["backend"] = "UP";
    const database: HealthStatus["database"] = body.components?.db?.status ?? "UNKNOWN";
    return { backend, database };
  } catch {
    return { backend: "DOWN", database: "UNKNOWN" };
  }
}
