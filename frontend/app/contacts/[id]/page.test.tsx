import { render, screen, waitFor } from "@testing-library/react";
import ContactDetailView from "./ContactDetailView";
import { fetchContact, fetchDealsForContact } from "../../../lib/contacts";
import { fetchUserDirectory } from "../../../lib/users";

jest.mock("../../../lib/contacts");
jest.mock("../../../lib/users");

const BASE_CONTACT = {
  id: "c1",
  contactType: "LEAD",
  firstName: "Maria",
  lastName: "Puig",
  email: "maria@example.com",
  phone: "+376123456",
  preferredLocale: "ca",
  source: "WALK_IN",
  sourceDetail: null,
  notes: "Interested in a 3-bedroom apartment",
  ownerUserId: "u1",
  createdAt: "2026-11-26T10:00:00Z",
  updatedAt: "2026-11-26T10:00:00Z",
};

describe("Contact detail view", () => {
  beforeEach(() => {
    (fetchContact as jest.Mock).mockResolvedValue(BASE_CONTACT);
    (fetchUserDirectory as jest.Mock).mockResolvedValue([{ id: "u1", fullName: "Alex Agent" }]);
    (fetchDealsForContact as jest.Mock).mockResolvedValue([]);
  });

  it("renders every field and the owner agent's name", async () => {
    render(<ContactDetailView id="c1" />);

    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());
    expect(screen.getByText("LEAD")).toBeInTheDocument();
    expect(screen.getByText("WALK_IN")).toBeInTheDocument();
    expect(screen.getByText("maria@example.com")).toBeInTheDocument();
    expect(screen.getByText("+376123456")).toBeInTheDocument();
    expect(screen.getByText("Interested in a 3-bedroom apartment")).toBeInTheDocument();
    expect(screen.getByText("Alex Agent")).toBeInTheDocument();
  });

  it("shows an Edit link to /contacts/{id}/edit", async () => {
    render(<ContactDetailView id="c1" />);

    await waitFor(() =>
      expect(screen.getByRole("link", { name: /edit/i })).toHaveAttribute("href", "/contacts/c1/edit"));
  });

  it("shows a New deal link to /contacts/{id}/deals/new", async () => {
    render(<ContactDetailView id="c1" />);

    await waitFor(() =>
      expect(screen.getByRole("link", { name: /new deal/i })).toHaveAttribute("href", "/contacts/c1/deals/new"));
  });

  it("lists the contact's deals fetched via GET /api/deals?contactId=", async () => {
    (fetchDealsForContact as jest.Mock).mockResolvedValue([
      { id: "d1", stage: "QUALIFIED", propertyReferenceCode: "P-001", assignedUserFullName: "Alex Agent" },
    ]);

    render(<ContactDetailView id="c1" />);

    await waitFor(() => expect(fetchDealsForContact).toHaveBeenCalledWith("c1"));
    expect(await screen.findByText("QUALIFIED")).toBeInTheDocument();
    expect(screen.getByText("P-001")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /qualified/i })).toHaveAttribute("href", "/deals/d1");
  });
});
