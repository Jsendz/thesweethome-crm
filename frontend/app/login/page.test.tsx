import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import LoginPage from "./page";
import { login } from "../../lib/auth";
import { useAuth } from "../../lib/AuthContext";

jest.mock("../../lib/auth");
jest.mock("../../lib/AuthContext");
const pushMock = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

describe("LoginPage", () => {
  const refreshMock = jest.fn();

  beforeEach(() => {
    pushMock.mockReset();
    refreshMock.mockReset().mockResolvedValue(undefined);
    (useAuth as jest.Mock).mockReturnValue({ user: null, loading: false, refresh: refreshMock });
  });

  it("refreshes the auth context before redirecting to / on successful login", async () => {
    (login as jest.Mock).mockResolvedValue(true);
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@b.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "pw" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/"));
    expect(refreshMock).toHaveBeenCalledTimes(1);
    expect(refreshMock.mock.invocationCallOrder[0]).toBeLessThan(pushMock.mock.invocationCallOrder[0]);
  });

  it("shows an error message on failed login", async () => {
    (login as jest.Mock).mockResolvedValue(false);
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@b.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));
    await waitFor(() => expect(screen.getByText(/incorrect email or password/i)).toBeInTheDocument());
    expect(refreshMock).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });
});
