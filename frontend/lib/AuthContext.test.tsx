import { render, screen, waitFor } from "@testing-library/react";
import { AuthProvider, useAuth } from "./AuthContext";
import { fetchMe } from "./auth";

jest.mock("./auth");

function Probe() {
  const { user, loading } = useAuth();
  if (loading) return <p>loading</p>;
  return <p>{user ? `logged in as ${user.email}` : "not logged in"}</p>;
}

describe("AuthProvider", () => {
  it("exposes the fetched user once loading completes", async () => {
    (fetchMe as jest.Mock).mockResolvedValue({ id: "1", email: "a@b.com", fullName: "A B", role: "ADMIN" });
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText("logged in as a@b.com")).toBeInTheDocument());
  });

  it("exposes null when there's no session", async () => {
    (fetchMe as jest.Mock).mockResolvedValue(null);
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText("not logged in")).toBeInTheDocument());
  });
});
