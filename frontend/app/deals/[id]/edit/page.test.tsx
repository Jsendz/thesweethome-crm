import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import DealEditView from "./DealEditView";
import { fetchDeal, updateDeal } from "../../../../lib/deals";
import { fetchProperties } from "../../../../lib/properties";
import { fetchUserDirectory } from "../../../../lib/users";

jest.mock("../../../../lib/deals", () => ({
  ...jest.requireActual("../../../../lib/deals"),
  fetchDeal: jest.fn(),
  updateDeal: jest.fn(),
}));
jest.mock("../../../../lib/properties");
jest.mock("../../../../lib/users");

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

describe("Deal edit view", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fetchDeal as jest.Mock).mockResolvedValue(BASE_DEAL);
    (fetchProperties as jest.Mock).mockResolvedValue([
      { id: "p1", referenceCode: "P-001", propertyType: "APARTMENT", operation: "SALE", status: "ACTIVE", price: 1000, thumbnailUrl: null },
      { id: "p2", referenceCode: "P-002", propertyType: "HOUSE", operation: "RENT", status: "ACTIVE", price: 2000, thumbnailUrl: null },
    ]);
    (fetchUserDirectory as jest.Mock).mockResolvedValue([
      { id: "u1", fullName: "Alex Agent" },
      { id: "u2", fullName: "Blair Broker" },
    ]);
  });

  it("loads the deal's current property, assigned agent, and expected close date into the form", async () => {
    render(<DealEditView id="d1" />);

    await waitFor(() => expect((screen.getByLabelText(/property/i) as HTMLSelectElement).value).toBe("p1"));
    expect((screen.getByLabelText(/assigned agent/i) as HTMLSelectElement).value).toBe("u1");
    expect(screen.getByLabelText(/expected close date/i)).toHaveValue("2026-12-15");
  });

  it("submits only the changed fields and shows a saved confirmation", async () => {
    (updateDeal as jest.Mock).mockResolvedValue({ ...BASE_DEAL, assignedUserId: "u2" });
    render(<DealEditView id="d1" />);
    await waitFor(() => expect((screen.getByLabelText(/property/i) as HTMLSelectElement).value).toBe("p1"));

    fireEvent.change(screen.getByLabelText(/assigned agent/i), { target: { value: "u2" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(updateDeal).toHaveBeenCalledWith("d1", { assignedUserId: "u2" }));
    expect(await screen.findByText(/saved/i)).toBeInTheDocument();
  });
});
