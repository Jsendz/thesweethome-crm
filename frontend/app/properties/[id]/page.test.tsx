import { render, screen, waitFor } from "@testing-library/react";
import PropertyDetailView from "./PropertyDetailView";
import { fetchProperty } from "../../../lib/properties";

jest.mock("../../../lib/properties");

const BASE_PROPERTY = {
  id: "abc",
  referenceCode: "P-001",
  propertyType: "APARTMENT",
  operation: "SALE",
  status: "ACTIVE",
  addressLine: "Carrer Major 1",
  parish: "Andorra la Vella",
  country: "AD",
  latitude: 42.5,
  longitude: 1.52,
  price: 250000,
  currency: "EUR",
  areaM2: 85,
  rooms: 3,
  bathrooms: 2,
  yearBuilt: 2010,
  energyRating: "B",
  isExclusive: false,
  exclusivityEndDate: null,
  translations: [{ locale: "ca", title: "Pis a Andorra la Vella", description: "Molt lluminós" }],
  media: [
    {
      id: "m1",
      mediaType: "PHOTO",
      variants: { card_jpeg: "http://localhost:9000/crm-media/properties/abc/m1/card_jpeg.jpg" },
      sortOrder: 0,
    },
  ],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("Property detail page", () => {
  it("renders every field, the Catalan translation, and a photo gallery from the media variants", async () => {
    (fetchProperty as jest.Mock).mockResolvedValue(BASE_PROPERTY);

    render(<PropertyDetailView id="abc" />);

    await waitFor(() => expect(screen.getByText("P-001")).toBeInTheDocument());
    expect(screen.getByText("Pis a Andorra la Vella")).toBeInTheDocument();
    expect(screen.getByText("Molt lluminós")).toBeInTheDocument();
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
    expect(screen.getByText("250000")).toBeInTheDocument();
    expect(screen.getByRole("img")).toHaveAttribute(
        "src", "http://localhost:9000/crm-media/properties/abc/m1/card_jpeg.jpg");
  });

  it("shows an Edit link to /properties/{id}/edit", async () => {
    (fetchProperty as jest.Mock).mockResolvedValue(BASE_PROPERTY);

    render(<PropertyDetailView id="abc" />);

    await waitFor(() =>
        expect(screen.getByRole("link", { name: /edit/i })).toHaveAttribute("href", "/properties/abc/edit"));
  });

  it("lists any additional translations already added", async () => {
    (fetchProperty as jest.Mock).mockResolvedValue({
      ...BASE_PROPERTY,
      translations: [
        ...BASE_PROPERTY.translations,
        { locale: "en", title: "English title", description: "English description" },
      ],
    });

    render(<PropertyDetailView id="abc" />);

    await waitFor(() => expect(screen.getByText("English title")).toBeInTheDocument());
    expect(screen.getByText("English description")).toBeInTheDocument();
  });
});
