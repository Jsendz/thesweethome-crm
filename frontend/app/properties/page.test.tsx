import { render, screen, waitFor } from "@testing-library/react";
import PropertiesPage from "./page";
import { useAuth } from "../../lib/AuthContext";
import { fetchProperties } from "../../lib/properties";

jest.mock("../../lib/AuthContext");
jest.mock("../../lib/properties");

describe("Properties list page", () => {
  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "a@b.com", role: "AGENT" }, loading: false });
  });

  it("renders each property's reference code, status, price, and thumbnail", async () => {
    (fetchProperties as jest.Mock).mockResolvedValue([
      {
        id: "1",
        referenceCode: "P-001",
        propertyType: "APARTMENT",
        operation: "SALE",
        status: "ACTIVE",
        price: 250000,
        thumbnailUrl: "http://localhost:9000/crm-media/properties/1/x/thumbnail_jpeg.jpg",
      },
    ]);

    render(<PropertiesPage />);

    await waitFor(() => expect(screen.getByText("P-001")).toBeInTheDocument());
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
    expect(screen.getByText("250000")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "P-001" })).toBeInTheDocument();
  });

  it("shows a New listing link to /properties/new", async () => {
    (fetchProperties as jest.Mock).mockResolvedValue([]);

    render(<PropertiesPage />);

    await waitFor(() =>
      expect(screen.getByRole("link", { name: /new listing/i })).toHaveAttribute("href", "/properties/new"));
  });

  it("shows an empty state when there are no properties yet", async () => {
    (fetchProperties as jest.Mock).mockResolvedValue([]);

    render(<PropertiesPage />);

    await waitFor(() => expect(screen.getByText(/no listings yet/i)).toBeInTheDocument());
  });
});
