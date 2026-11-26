import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import NewDealPage from "./page";
import { createDeal } from "../../../../../lib/deals";
import { fetchProperties } from "../../../../../lib/properties";
import { fetchUserDirectory } from "../../../../../lib/users";
import { useAuth } from "../../../../../lib/AuthContext";

jest.mock("../../../../../lib/deals");
jest.mock("../../../../../lib/properties");
jest.mock("../../../../../lib/users");
jest.mock("../../../../../lib/AuthContext");
const pushMock = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
  useParams: () => ({ id: "c1" }),
}));

describe("New deal page", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useAuth as jest.Mock).mockReturnValue({ user: { id: "u1", email: "a@b.com", role: "AGENT" }, loading: false });
    (fetchProperties as jest.Mock).mockResolvedValue([
      { id: "p1", referenceCode: "P-001", propertyType: "APARTMENT", operation: "SALE", status: "ACTIVE", price: 1000, thumbnailUrl: null },
    ]);
    (fetchUserDirectory as jest.Mock).mockResolvedValue([
      { id: "u1", fullName: "Alex Agent" },
      { id: "u2", fullName: "Blair Broker" },
    ]);
  });

  it("submits the deal scoped to this contact and redirects to its detail page", async () => {
    (createDeal as jest.Mock).mockResolvedValue({ id: "d1" });
    render(<NewDealPage />);
    await waitFor(() => expect(screen.getByText("P-001")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /create deal/i }));

    await waitFor(() => expect(createDeal).toHaveBeenCalledWith(
      expect.objectContaining({ contactId: "c1", assignedUserId: "u1" }),
    ));
    expect(pushMock).toHaveBeenCalledWith("/deals/d1");
  });

  it("lets an existing property be picked from a select, or left unset", async () => {
    (createDeal as jest.Mock).mockResolvedValue({ id: "d1" });
    render(<NewDealPage />);
    await waitFor(() => expect(screen.getByText("P-001")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/property/i), { target: { value: "p1" } });
    fireEvent.click(screen.getByRole("button", { name: /create deal/i }));

    await waitFor(() => expect(createDeal).toHaveBeenCalledWith(
      expect.objectContaining({ propertyId: "p1" }),
    ));
  });

  it("defaults the assigned-agent select to the current user", async () => {
    render(<NewDealPage />);
    await waitFor(() => expect(screen.getByText("P-001")).toBeInTheDocument());

    const select = screen.getByLabelText(/assigned agent/i) as HTMLSelectElement;
    expect(select.value).toBe("u1");
  });
});
