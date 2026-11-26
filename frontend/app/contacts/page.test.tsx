import { render, screen, waitFor } from "@testing-library/react";
import ContactsPage from "./page";
import { useAuth } from "../../lib/AuthContext";
import { fetchContacts } from "../../lib/contacts";
import { fetchUserDirectory } from "../../lib/users";

jest.mock("../../lib/AuthContext");
jest.mock("../../lib/contacts");
jest.mock("../../lib/users");

describe("Contacts list page", () => {
  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ user: { id: "u1", email: "a@b.com", role: "AGENT" }, loading: false });
    (fetchUserDirectory as jest.Mock).mockResolvedValue([{ id: "u1", fullName: "Alex Agent" }]);
  });

  it("renders each contact's name, type, source, and owner agent", async () => {
    (fetchContacts as jest.Mock).mockResolvedValue([
      {
        id: "c1",
        contactType: "LEAD",
        firstName: "Maria",
        lastName: "Puig",
        email: null,
        phone: null,
        preferredLocale: null,
        source: "WALK_IN",
        sourceDetail: null,
        notes: null,
        ownerUserId: "u1",
        createdAt: "2026-11-26T10:00:00Z",
        updatedAt: "2026-11-26T10:00:00Z",
      },
    ]);

    render(<ContactsPage />);

    await waitFor(() => expect(screen.getByText("Maria Puig")).toBeInTheDocument());
    expect(screen.getByText("LEAD")).toBeInTheDocument();
    expect(screen.getByText("WALK_IN")).toBeInTheDocument();
    expect(screen.getByText("Alex Agent")).toBeInTheDocument();
  });

  it("shows a New contact link to /contacts/new", async () => {
    (fetchContacts as jest.Mock).mockResolvedValue([]);

    render(<ContactsPage />);

    await waitFor(() =>
      expect(screen.getByRole("link", { name: /new contact/i })).toHaveAttribute("href", "/contacts/new"));
  });

  it("shows an empty state when there are no contacts yet", async () => {
    (fetchContacts as jest.Mock).mockResolvedValue([]);

    render(<ContactsPage />);

    await waitFor(() => expect(screen.getByText(/no contacts yet/i)).toBeInTheDocument());
  });
});
