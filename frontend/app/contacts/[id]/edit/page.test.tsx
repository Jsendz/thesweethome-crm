import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import ContactEditView from "./ContactEditView";
import { fetchContact, updateContact } from "../../../../lib/contacts";
import { fetchUserDirectory } from "../../../../lib/users";

jest.mock("../../../../lib/contacts");
jest.mock("../../../../lib/users");

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

describe("Contact edit view", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fetchContact as jest.Mock).mockResolvedValue(BASE_CONTACT);
    (fetchUserDirectory as jest.Mock).mockResolvedValue([
      { id: "u1", fullName: "Alex Agent" },
      { id: "u2", fullName: "Blair Broker" },
    ]);
  });

  it("loads the contact's current values into the form", async () => {
    render(<ContactEditView id="c1" />);

    await waitFor(() => expect(screen.getByLabelText(/first name/i)).toHaveValue("Maria"));
    expect(screen.getByLabelText(/last name/i)).toHaveValue("Puig");
    expect(screen.getByLabelText(/^email$/i)).toHaveValue("maria@example.com");
    expect((screen.getByLabelText(/owner agent/i) as HTMLSelectElement).value).toBe("u1");
  });

  it("submits only the changed fields and shows a saved confirmation", async () => {
    (updateContact as jest.Mock).mockResolvedValue({ ...BASE_CONTACT, notes: "Called back" });
    render(<ContactEditView id="c1" />);
    await waitFor(() => expect(screen.getByLabelText(/first name/i)).toHaveValue("Maria"));

    fireEvent.change(screen.getByLabelText(/notes/i), { target: { value: "Called back" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(updateContact).toHaveBeenCalledWith("c1", { notes: "Called back" }));
    expect(await screen.findByText(/saved/i)).toBeInTheDocument();
  });

  // Final-review fix: same missing-field gap as the create form.
  it("loads the current preferred locale and submits a changed one", async () => {
    (updateContact as jest.Mock).mockResolvedValue({ ...BASE_CONTACT, preferredLocale: "es" });
    render(<ContactEditView id="c1" />);
    await waitFor(() =>
      expect((screen.getByLabelText(/preferred locale/i) as HTMLSelectElement).value).toBe("ca"));

    fireEvent.change(screen.getByLabelText(/preferred locale/i), { target: { value: "es" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(updateContact).toHaveBeenCalledWith("c1", { preferredLocale: "es" }));
  });
});
