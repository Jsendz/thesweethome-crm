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

  it("shows the backend as up even while the database is down", async () => {
    // Regression guard: Actuator reports the backend's overall status as
    // DOWN whenever the db component is down, but the backend process
    // itself is still reachable and running — the page must not conflate
    // the two (see frontend/lib/health.ts and health.test.ts).
    (fetchHealth as jest.Mock).mockResolvedValue({ backend: "UP", database: "DOWN" });
    render(await Page());
    expect(screen.getByText(/Backend: UP/i)).toBeInTheDocument();
    expect(screen.getByText(/Database: DOWN/i)).toBeInTheDocument();
  });
});
