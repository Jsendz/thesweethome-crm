import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import NewPropertyPage from "./page";
import { createProperty } from "../../../lib/properties";

jest.mock("../../../lib/properties");
const pushMock = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

describe("New listing page", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("submits all entered fields and redirects to the edit page for the new property", async () => {
    (createProperty as jest.Mock).mockResolvedValue({ id: "abc" });
    render(<NewPropertyPage />);

    fireEvent.change(screen.getByLabelText(/reference code/i), { target: { value: "P-100" } });
    fireEvent.change(screen.getByLabelText(/price/i), { target: { value: "150000" } });
    fireEvent.change(screen.getByLabelText(/catalan title/i), { target: { value: "Títol en català" } });
    fireEvent.click(screen.getByRole("button", { name: /create listing/i }));

    await waitFor(() => expect(createProperty).toHaveBeenCalledWith(
        expect.objectContaining({ referenceCode: "P-100", price: 150000, caTitle: "Títol en català" })));
    expect(pushMock).toHaveBeenCalledWith("/properties/abc/edit");
  });

  it("shows a validation error when reference code, price, or the Catalan title is missing", async () => {
    render(<NewPropertyPage />);

    fireEvent.click(screen.getByRole("button", { name: /create listing/i }));

    expect(await screen.findByText(/required/i)).toBeInTheDocument();
    expect(createProperty).not.toHaveBeenCalled();
  });

  it("shows the server's error message when the reference code is already taken", async () => {
    (createProperty as jest.Mock).mockRejectedValue(new Error("This reference code is already in use."));
    render(<NewPropertyPage />);

    fireEvent.change(screen.getByLabelText(/reference code/i), { target: { value: "P-DUP" } });
    fireEvent.change(screen.getByLabelText(/price/i), { target: { value: "1000" } });
    fireEvent.change(screen.getByLabelText(/catalan title/i), { target: { value: "X" } });
    fireEvent.click(screen.getByRole("button", { name: /create listing/i }));

    expect(await screen.findByText(/already in use/i)).toBeInTheDocument();
  });
});
