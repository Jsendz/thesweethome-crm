export type HealthStatus = {
  backend: "UP" | "DOWN";
  database: "UP" | "DOWN" | "UNKNOWN";
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function fetchHealth(): Promise<HealthStatus> {
  try {
    const response = await fetch(`${API_BASE_URL}/actuator/health`);
    const body = await response.json();
    const backend: HealthStatus["backend"] = body.status === "UP" ? "UP" : "DOWN";
    const database: HealthStatus["database"] = body.components?.db?.status ?? "UNKNOWN";
    return { backend, database };
  } catch {
    return { backend: "DOWN", database: "UNKNOWN" };
  }
}
