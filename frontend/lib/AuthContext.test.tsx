import { render, screen, waitFor } from "@testing-library/react";
import { AuthProvider, useAuth } from "./AuthContext";
import { fetchMe } from "./auth";

jest.mock("./auth");

const pushMock = jest.fn();
let mockPathname = "/";
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
  usePathname: () => mockPathname,
}));

function Probe() {
  const { user, loading } = useAuth();
  if (loading) return <p>loading</p>;
  return <p>{user ? `logged in as ${user.email}` : "not logged in"}</p>;
}

describe("AuthProvider", () => {
  beforeEach(() => {
    pushMock.mockReset();
    mockPathname = "/";
  });

  it("exposes the fetched user once loading completes", async () => {
    (fetchMe as jest.Mock).mockResolvedValue({ id: "1", email: "a@b.com", fullName: "A B", role: "ADMIN" });
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText("logged in as a@b.com")).toBeInTheDocument());
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("exposes null when there's no session", async () => {
    (fetchMe as jest.Mock).mockResolvedValue(null);
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText("not logged in")).toBeInTheDocument());
  });

  it("redirects to /login when there's no session and we're not already there", async () => {
    mockPathname = "/users";
    (fetchMe as jest.Mock).mockResolvedValue(null);
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/login"));
  });

  it("does not redirect when there's no session but we're already on /login", async () => {
    mockPathname = "/login";
    (fetchMe as jest.Mock).mockResolvedValue(null);
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText("not logged in")).toBeInTheDocument());
    expect(pushMock).not.toHaveBeenCalled();
  });
});
