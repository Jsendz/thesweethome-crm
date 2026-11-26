import { render, screen, waitFor } from "@testing-library/react";
import DealsPage from "./page";
import { useAuth } from "../../lib/AuthContext";
import { fetchDeals } from "../../lib/deals";

jest.mock("../../lib/AuthContext");
// Partial mock: DEAL_STAGES is a plain constant the page itself relies on
// for sort order, not something to fake - a full jest.mock("../../lib/deals")
// auto-mock replaces it with an empty array (indexOf always -1), silently
// turning the sort into a no-op. Keep the real constant, mock only the
// network calls.
jest.mock("../../lib/deals", () => ({
  ...jest.requireActual("../../lib/deals"),
  fetchDeals: jest.fn(),
}));

describe("Deals pipeline list page", () => {
  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ user: { id: "u1", email: "a@b.com", role: "AGENT" }, loading: false });
  });

  it("renders each deal's contact name, property reference, stage, assigned agent, and expected close date", async () => {
    (fetchDeals as jest.Mock).mockResolvedValue([
      {
        id: "d1",
        contactName: "Maria Puig",
        propertyReferenceCode: "P-001",
        stage: "QUALIFIED",
        assignedUserFullName: "Alex Agent",
        expectedCloseDate: "2026-12-15",
      },
    ]);

    render(<DealsPage />);

    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());
    expect(screen.getByText("P-001")).toBeInTheDocument();
    expect(screen.getByText("QUALIFIED")).toBeInTheDocument();
    expect(screen.getByText("Alex Agent")).toBeInTheDocument();
    expect(screen.getByText("2026-12-15")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /maria puig/i })).toHaveAttribute("href", "/deals/d1");
  });

  it("sorts the list by stage", async () => {
    (fetchDeals as jest.Mock).mockResolvedValue([
      { id: "d1", contactName: "Closed Won Deal", propertyReferenceCode: null, stage: "CLOSED_WON", assignedUserFullName: "Alex Agent", expectedCloseDate: null },
      { id: "d2", contactName: "Lead Deal", propertyReferenceCode: null, stage: "LEAD", assignedUserFullName: "Alex Agent", expectedCloseDate: null },
      { id: "d3", contactName: "Qualified Deal", propertyReferenceCode: null, stage: "QUALIFIED", assignedUserFullName: "Alex Agent", expectedCloseDate: null },
    ]);

    render(<DealsPage />);

    await waitFor(() => expect(screen.getByText("Lead Deal")).toBeInTheDocument());
    const body = document.body.textContent ?? "";
    expect(body.indexOf("Lead Deal")).toBeLessThan(body.indexOf("Qualified Deal"));
    expect(body.indexOf("Qualified Deal")).toBeLessThan(body.indexOf("Closed Won Deal"));
  });

  it("shows an empty state when there are no deals yet", async () => {
    (fetchDeals as jest.Mock).mockResolvedValue([]);

    render(<DealsPage />);

    await waitFor(() => expect(screen.getByText(/no deals yet/i)).toBeInTheDocument());
  });
});
