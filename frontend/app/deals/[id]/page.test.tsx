import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import DealDetailView from "./DealDetailView";
import { fetchDeal, fetchActivities, updateDeal, logActivity } from "../../../lib/deals";

jest.mock("../../../lib/deals", () => ({
  ...jest.requireActual("../../../lib/deals"),
  fetchDeal: jest.fn(),
  fetchActivities: jest.fn(),
  updateDeal: jest.fn(),
  logActivity: jest.fn(),
}));

const BASE_DEAL = {
  id: "d1",
  contactId: "c1",
  contactName: "Maria Puig",
  propertyId: "p1",
  propertyReferenceCode: "P-001",
  assignedUserId: "u1",
  assignedUserFullName: "Alex Agent",
  stage: "LEAD",
  lostReason: null,
  coldReason: null,
  coldMarkedByUserId: null,
  coldMarkedAt: null,
  expectedCloseDate: "2026-12-15",
  createdAt: "2026-11-26T10:00:00Z",
  updatedAt: "2026-11-26T10:00:00Z",
};

describe("Deal detail view", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fetchDeal as jest.Mock).mockResolvedValue(BASE_DEAL);
    (fetchActivities as jest.Mock).mockResolvedValue([]);
  });

  it("renders the deal's fields and an Edit link to /deals/{id}/edit", async () => {
    render(<DealDetailView id="d1" />);

    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());
    expect(screen.getByText("P-001")).toBeInTheDocument();
    expect(screen.getByText("Alex Agent")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /edit/i })).toHaveAttribute("href", "/deals/d1/edit");
  });

  it("changing the stage select to an ordinary value saves it immediately", async () => {
    (updateDeal as jest.Mock).mockResolvedValue({ ...BASE_DEAL, stage: "QUALIFIED" });
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/stage/i), { target: { value: "QUALIFIED" } });

    await waitFor(() => expect(updateDeal).toHaveBeenCalledWith("d1", { stage: "QUALIFIED" }));
  });

  it("Mark as cold prompts for a reason and saves stage=COLD with it", async () => {
    (updateDeal as jest.Mock).mockResolvedValue({ ...BASE_DEAL, stage: "COLD", coldReason: "Went quiet" });
    jest.spyOn(window, "prompt").mockReturnValue("Went quiet");
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /mark as cold/i }));

    await waitFor(() => expect(updateDeal).toHaveBeenCalledWith("d1", { stage: "COLD", coldReason: "Went quiet" }));
  });

  it("does not submit Mark as cold if the reason prompt is cancelled or left blank", async () => {
    jest.spyOn(window, "prompt").mockReturnValue(null);
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /mark as cold/i }));

    await waitFor(() => expect(updateDeal).not.toHaveBeenCalled());
  });

  it("Close as lost prompts for a reason and saves stage=CLOSED_LOST with it", async () => {
    (updateDeal as jest.Mock).mockResolvedValue({ ...BASE_DEAL, stage: "CLOSED_LOST", lostReason: "Chose a rival" });
    jest.spyOn(window, "prompt").mockReturnValue("Chose a rival");
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /close as lost/i }));

    await waitFor(() =>
      expect(updateDeal).toHaveBeenCalledWith("d1", { stage: "CLOSED_LOST", lostReason: "Chose a rival" }));
  });

  it("logs a new activity via the mini-form and shows it at the top of the activity list", async () => {
    (logActivity as jest.Mock).mockResolvedValue({
      id: "a1",
      dealId: "d1",
      contactId: "c1",
      propertyId: "p1",
      userId: "u1",
      userFullName: "Alex Agent",
      activityType: "CALL",
      status: "COMPLETED",
      notes: "Left a voicemail",
      occurredAt: "2026-11-26T12:00:00Z",
      createdAt: "2026-11-26T12:00:00Z",
    });
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/activity type/i), { target: { value: "CALL" } });
    fireEvent.change(screen.getByLabelText(/notes/i), { target: { value: "Left a voicemail" } });
    fireEvent.click(screen.getByRole("button", { name: /log activity/i }));

    await waitFor(() => expect(logActivity).toHaveBeenCalled());
    expect(await screen.findByText("Left a voicemail")).toBeInTheDocument();
  });

  it("a logged VIEWING activity with no status chosen shows as SCHEDULED", async () => {
    (logActivity as jest.Mock).mockResolvedValue({
      id: "a2",
      dealId: "d1",
      contactId: "c1",
      propertyId: "p1",
      userId: "u1",
      userFullName: "Alex Agent",
      activityType: "VIEWING",
      status: "SCHEDULED",
      notes: null,
      occurredAt: "2026-11-27T15:00:00Z",
      createdAt: "2026-11-26T12:00:00Z",
    });
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/activity type/i), { target: { value: "VIEWING" } });
    fireEvent.click(screen.getByRole("button", { name: /log activity/i }));

    await waitFor(() => expect(logActivity).toHaveBeenCalledWith(
      "d1",
      expect.not.objectContaining({ status: expect.anything() }),
    ));
    expect(await screen.findByText("SCHEDULED")).toBeInTheDocument();
  });
});
