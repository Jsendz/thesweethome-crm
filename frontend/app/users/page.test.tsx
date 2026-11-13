import { render, screen, waitFor } from "@testing-library/react";
import UsersPage from "./page";
import { useAuth } from "../../lib/AuthContext";

jest.mock("../../lib/AuthContext");
const pushMock = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

describe("UsersPage", () => {
  it("redirects a non-admin to /", async () => {
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "a@b.com", role: "AGENT" }, loading: false });
    render(<UsersPage />);
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/"));
  });

  it("renders the user list for an admin", async () => {
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "admin@b.com", role: "ADMIN" }, loading: false });
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => [{ id: "1", email: "agent@b.com", fullName: "Agent One", role: "AGENT", active: true }],
    }) as jest.Mock;
    render(<UsersPage />);
    await waitFor(() => expect(screen.getByText("agent@b.com")).toBeInTheDocument());
  });
});
