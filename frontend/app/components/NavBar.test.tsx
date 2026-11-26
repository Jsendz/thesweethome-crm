import { render, screen } from "@testing-library/react";
import { NavBar } from "./NavBar";
import { useAuth } from "../../lib/AuthContext";

jest.mock("../../lib/AuthContext");

describe("NavBar", () => {
  it("shows who's logged in and a users link for an admin", () => {
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "a@b.com", role: "ADMIN" }, loading: false });
    render(<NavBar />);
    expect(screen.getByText(/a@b.com/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /users/i })).toBeInTheDocument();
  });

  it("hides the users link for a non-admin", () => {
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "a@b.com", role: "AGENT" }, loading: false });
    render(<NavBar />);
    expect(screen.queryByRole("link", { name: /users/i })).not.toBeInTheDocument();
  });

  it("renders nothing when not logged in", () => {
    (useAuth as jest.Mock).mockReturnValue({ user: null, loading: false });
    const { container } = render(<NavBar />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a Listings link for any logged-in user", () => {
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "a@b.com", role: "AGENT" }, loading: false });
    render(<NavBar />);
    expect(screen.getByRole("link", { name: /listings/i })).toHaveAttribute("href", "/properties");
  });

  it("shows a Listings link for a manager too", () => {
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "m@b.com", role: "MANAGER" }, loading: false });
    render(<NavBar />);
    expect(screen.getByRole("link", { name: /listings/i })).toBeInTheDocument();
  });
});
