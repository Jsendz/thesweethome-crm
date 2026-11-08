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

  it("returns backend UP and database DOWN when the backend answers unhealthy", async () => {
    // Actuator rolls component status up to the top level and answers 503,
    // but the backend itself is reachable and responding.
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 503,
      json: async () => ({ status: "DOWN", components: { db: { status: "DOWN" } } }),
    }) as jest.Mock;

    const result = await fetchHealth();
    expect(result).toEqual({ backend: "UP", database: "DOWN" });
  });

  it("returns backend DOWN and database UNKNOWN when the fetch itself fails", async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error("network error"));

    const result = await fetchHealth();
    expect(result).toEqual({ backend: "DOWN", database: "UNKNOWN" });
  });
});
