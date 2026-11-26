import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import NewContactPage from "./page";
import { useAuth } from "../../../lib/AuthContext";
import { createContact } from "../../../lib/contacts";
import { fetchUserDirectory } from "../../../lib/users";

jest.mock("../../../lib/AuthContext");
jest.mock("../../../lib/contacts");
jest.mock("../../../lib/users");
const pushMock = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

describe("New contact page", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useAuth as jest.Mock).mockReturnValue({
      user: { id: "u1", email: "a@b.com", fullName: "Alex Agent", role: "AGENT" },
      loading: false,
    });
    (fetchUserDirectory as jest.Mock).mockResolvedValue([
      { id: "u1", fullName: "Alex Agent" },
      { id: "u2", fullName: "Blair Broker" },
    ]);
  });

  it("submits all entered fields and redirects to the new contact's detail page", async () => {
    (createContact as jest.Mock).mockResolvedValue({ id: "new-contact-id" });
    render(<NewContactPage />);
    await waitFor(() => expect(screen.getByText("Alex Agent")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/first name/i), { target: { value: "Maria" } });
    fireEvent.change(screen.getByLabelText(/last name/i), { target: { value: "Puig" } });
    fireEvent.change(screen.getByLabelText(/contact type/i), { target: { value: "LEAD" } });
    fireEvent.change(screen.getByLabelText(/^source$/i), { target: { value: "WALK_IN" } });
    fireEvent.click(screen.getByRole("button", { name: /create contact/i }));

    await waitFor(() => expect(createContact).toHaveBeenCalledWith(
      expect.objectContaining({
        firstName: "Maria",
        lastName: "Puig",
        contactType: "LEAD",
        source: "WALK_IN",
        ownerUserId: "u1",
      }),
    ));
    expect(pushMock).toHaveBeenCalledWith("/contacts/new-contact-id");
  });

  it("shows a validation error when first name, last name, type, or source is missing", async () => {
    render(<NewContactPage />);
    await waitFor(() => expect(screen.getByText("Alex Agent")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /create contact/i }));

    expect(await screen.findByText(/required/i)).toBeInTheDocument();
    expect(createContact).not.toHaveBeenCalled();
  });

  it("defaults the owner-agent select to the current user and lets a different one be picked", async () => {
    (createContact as jest.Mock).mockResolvedValue({ id: "new-contact-id" });
    render(<NewContactPage />);
    await waitFor(() => expect(screen.getByText("Alex Agent")).toBeInTheDocument());

    const ownerSelect = screen.getByLabelText(/owner agent/i) as HTMLSelectElement;
    expect(ownerSelect.value).toBe("u1");

    fireEvent.change(ownerSelect, { target: { value: "u2" } });
    fireEvent.change(screen.getByLabelText(/first name/i), { target: { value: "Maria" } });
    fireEvent.change(screen.getByLabelText(/last name/i), { target: { value: "Puig" } });
    fireEvent.change(screen.getByLabelText(/contact type/i), { target: { value: "LEAD" } });
    fireEvent.change(screen.getByLabelText(/^source$/i), { target: { value: "WALK_IN" } });
    fireEvent.click(screen.getByRole("button", { name: /create contact/i }));

    await waitFor(() => expect(createContact).toHaveBeenCalledWith(
      expect.objectContaining({ ownerUserId: "u2" }),
    ));
  });
});
