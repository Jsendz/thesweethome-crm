import { render, screen, waitFor, fireEvent, within } from "@testing-library/react";
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

  // Final-review fix: the contact name heading was plain text, leaving no
  // way back to the contact this deal belongs to.
  it("links the contact's name back to its detail page", async () => {
    render(<DealDetailView id="d1" />);

    await waitFor(() =>
      expect(screen.getByRole("link", { name: "Maria Puig" })).toHaveAttribute("href", "/contacts/c1"));
  });

  it("changing the stage select to an ordinary value saves it immediately", async () => {
    (updateDeal as jest.Mock).mockResolvedValue({ ...BASE_DEAL, stage: "QUALIFIED" });
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/stage/i), { target: { value: "QUALIFIED" } });

    await waitFor(() => expect(updateDeal).toHaveBeenCalledWith("d1", { stage: "QUALIFIED" }));
  });

  // Final-review fix: COLD and CLOSED_LOST each require a reason collected
  // by the dedicated buttons below - the plain stage dropdown has no way to
  // collect one, so picking either directly there always 400s.
  it("does not offer COLD or CLOSED_LOST as plain stage-dropdown choices", async () => {
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    const stageSelect = screen.getByLabelText(/stage/i) as HTMLSelectElement;
    const coldOption = within(stageSelect).getByRole("option", { name: "COLD" }) as HTMLOptionElement;
    const lostOption = within(stageSelect).getByRole("option", { name: "CLOSED_LOST" }) as HTMLOptionElement;

    expect(coldOption.disabled).toBe(true);
    expect(lostOption.disabled).toBe(true);
  });

  // Final-review fix: an ordinary stage change that 400s (or fails for any
  // other reason) used to disappear silently - the select would just snap
  // back with no explanation.
  it("shows an error message when saving an ordinary stage change fails", async () => {
    (updateDeal as jest.Mock).mockRejectedValue(new Error("Could not save the deal."));
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/stage/i), { target: { value: "QUALIFIED" } });

    expect(await screen.findByText("Could not save the deal.")).toBeInTheDocument();
  });

  it("Mark as cold prompts for a reason and saves stage=COLD with it", async () => {
    (updateDeal as jest.Mock).mockResolvedValue({ ...BASE_DEAL, stage: "COLD", coldReason: "Went quiet" });
    jest.spyOn(window, "prompt").mockReturnValue("Went quiet");
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /mark as cold/i }));

    await waitFor(() => expect(updateDeal).toHaveBeenCalledWith("d1", { stage: "COLD", coldReason: "Went quiet" }));
  });

  it("does not submit Mark as cold if the reason prompt is cancelled", async () => {
    jest.spyOn(window, "prompt").mockReturnValue(null);
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /mark as cold/i }));

    await waitFor(() => expect(updateDeal).not.toHaveBeenCalled());
  });

  // Final-review coverage gap: the "or left blank" half of the above was
  // never actually exercised - window.prompt returning null (Cancel) and
  // window.prompt returning "" (OK with nothing typed) are different
  // mocked return values and both need their own case.
  it("does not submit Mark as cold if the reason prompt is left blank", async () => {
    jest.spyOn(window, "prompt").mockReturnValue("   ");
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /mark as cold/i }));

    await waitFor(() => expect(updateDeal).not.toHaveBeenCalled());
  });

  // Final-review fix: Mark as cold / Close as lost had no error handling at
  // all - a 400 or network failure vanished with no feedback.
  it("shows an error message when Mark as cold fails", async () => {
    (updateDeal as jest.Mock).mockRejectedValue(new Error("Could not save the deal."));
    jest.spyOn(window, "prompt").mockReturnValue("Went quiet");
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /mark as cold/i }));

    expect(await screen.findByText("Could not save the deal.")).toBeInTheDocument();
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

  it("shows an error message when Close as lost fails", async () => {
    (updateDeal as jest.Mock).mockRejectedValue(new Error("Could not save the deal."));
    jest.spyOn(window, "prompt").mockReturnValue("Chose a rival");
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /close as lost/i }));

    expect(await screen.findByText("Could not save the deal.")).toBeInTheDocument();
  });

  // Final-review fix: this test's assertion matched the mini-form's own
  // <textarea> (which still displayed the typed-in text at the moment
  // findByText first succeeded), not anything actually rendered from the
  // server's response - it kept passing even when the mocked logActivity
  // response returned completely different notes. Scoping every assertion
  // to the activity list's own role="list" element closes that hole.
  it("logs a new activity via the mini-form and shows it in the activity list", async () => {
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

    const list = screen.getByRole("list");
    await waitFor(() => expect(within(list).getByText("Left a voicemail")).toBeInTheDocument());
    expect(within(list).getByText("Alex Agent")).toBeInTheDocument();
  });

  // Final-review coverage gap: every prior test logged into an empty list,
  // so "at the top" was never actually distinguished from "the only item".
  // This seeds an existing, later-occurring activity and confirms a
  // backdated one logged afterward is inserted by occurred-at order, not
  // shoved to the front unconditionally.
  it("orders activities by occurred-at, not by when they were logged", async () => {
    (fetchActivities as jest.Mock).mockResolvedValue([
      {
        id: "existing",
        dealId: "d1",
        contactId: "c1",
        propertyId: "p1",
        userId: "u1",
        userFullName: "Alex Agent",
        activityType: "EMAIL",
        status: "COMPLETED",
        notes: "Sent the brochure",
        occurredAt: "2026-11-26T12:00:00Z",
        createdAt: "2026-11-26T12:00:00Z",
      },
    ]);
    (logActivity as jest.Mock).mockResolvedValue({
      id: "backdated",
      dealId: "d1",
      contactId: "c1",
      propertyId: "p1",
      userId: "u1",
      userFullName: "Alex Agent",
      activityType: "CALL",
      status: "COMPLETED",
      notes: "An earlier call, logged late",
      occurredAt: "2026-11-20T09:00:00Z",
      createdAt: "2026-11-26T12:00:00Z",
    });
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/notes/i), { target: { value: "An earlier call, logged late" } });
    fireEvent.click(screen.getByRole("button", { name: /log activity/i }));

    const list = screen.getByRole("list");
    await waitFor(() => expect(within(list).getByText("An earlier call, logged late")).toBeInTheDocument());
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent("Sent the brochure");
    expect(items[1]).toHaveTextContent("An earlier call, logged late");
  });

  // Final-review fix: the list showed only type/status/notes - no occurred-
  // at time and no who-logged-it, both of which the backend already sends.
  it("shows each activity's occurred-at time and who logged it", async () => {
    (fetchActivities as jest.Mock).mockResolvedValue([
      {
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
      },
    ]);
    render(<DealDetailView id="d1" />);

    const list = await screen.findByRole("list");
    expect(within(list).getByText("Alex Agent")).toBeInTheDocument();
    expect(within(list).getByText(new Date("2026-11-26T12:00:00Z").toLocaleString())).toBeInTheDocument();
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
    const list = screen.getByRole("list");
    expect(await within(list).findByText("SCHEDULED")).toBeInTheDocument();
  });

  it("shows an error message when logging an activity fails", async () => {
    (logActivity as jest.Mock).mockRejectedValue(new Error("Could not log the activity."));
    render(<DealDetailView id="d1" />);
    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /log activity/i }));

    expect(await screen.findByText("Could not log the activity.")).toBeInTheDocument();
  });

  // Final-review fix: the default "occurred at" was built from
  // toISOString(), which is always UTC - on a browser in any other
  // timezone this silently logged every activity 1-2+ hours off from the
  // actual local time the user saw in the picker.
  it("defaults the activity's occurred-at time to the current LOCAL time, not UTC", async () => {
    const getFullYear = jest.spyOn(Date.prototype, "getFullYear").mockReturnValue(2026);
    const getMonth = jest.spyOn(Date.prototype, "getMonth").mockReturnValue(10); // November
    const getDate = jest.spyOn(Date.prototype, "getDate").mockReturnValue(27);
    const getHours = jest.spyOn(Date.prototype, "getHours").mockReturnValue(1);
    const getMinutes = jest.spyOn(Date.prototype, "getMinutes").mockReturnValue(30);

    try {
      render(<DealDetailView id="d1" />);
      await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());

      // toISOString() is spec'd to read the Date's internal UTC time
      // directly, not through these prototype getters, so the old
      // `toISOString().slice(0, 16)` implementation is unaffected by this
      // mock and would still show the real (UTC) wall-clock time here -
      // only a local-getter-based implementation reflects it.
      expect(screen.getByLabelText(/occurred at/i)).toHaveValue("2026-11-27T01:30");
    } finally {
      getFullYear.mockRestore();
      getMonth.mockRestore();
      getDate.mockRestore();
      getHours.mockRestore();
      getMinutes.mockRestore();
    }
  });
});
