import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import LoginPage from "./page";
import { login } from "../../lib/auth";

jest.mock("../../lib/auth");
const pushMock = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

describe("LoginPage", () => {
  it("redirects to / on successful login", async () => {
    (login as jest.Mock).mockResolvedValue(true);
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@b.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "pw" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/"));
  });

  it("shows an error message on failed login", async () => {
    (login as jest.Mock).mockResolvedValue(false);
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@b.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));
    await waitFor(() => expect(screen.getByText(/incorrect email or password/i)).toBeInTheDocument());
  });
});
