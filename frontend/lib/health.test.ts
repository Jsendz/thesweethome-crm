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
