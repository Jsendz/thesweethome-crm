import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import PropertyEditView from "./PropertyEditView";
import { fetchProperty, updateProperty, addTranslation } from "../../../../lib/properties";

jest.mock("../../../../lib/properties");
jest.mock("../../../components/PhotoUploader", () => ({
  __esModule: true,
  default: ({ propertyId, media }: { propertyId: string; media: unknown[] }) => (
    <div data-testid="photo-uploader">
      PhotoUploader for {propertyId} with {media.length} photo(s)
    </div>
  ),
}));

const PROPERTY = {
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
    { id: "m1", mediaType: "PHOTO", variants: { thumbnail_jpeg: "http://x/m1.jpg" }, sortOrder: 0 },
  ],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("Edit listing page", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fetchProperty as jest.Mock).mockResolvedValue(PROPERTY);
  });

  it("loads the property's current values into the form", async () => {
    render(<PropertyEditView id="abc" />);

    await waitFor(() => expect(screen.getByLabelText(/reference code/i)).toHaveValue("P-001"));
    expect(screen.getByLabelText(/price/i)).toHaveValue(250000);
    expect(screen.getByLabelText(/catalan title/i)).toHaveValue("Pis a Andorra la Vella");
  });

  it("submits only the changed fields and shows a saved confirmation", async () => {
    (updateProperty as jest.Mock).mockResolvedValue({ ...PROPERTY, price: 260000 });
    render(<PropertyEditView id="abc" />);
    await waitFor(() => expect(screen.getByLabelText(/reference code/i)).toHaveValue("P-001"));

    fireEvent.change(screen.getByLabelText(/^price/i), { target: { value: "260000" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(updateProperty).toHaveBeenCalledWith("abc", { price: 260000 }));
    expect(await screen.findByText(/saved/i)).toBeInTheDocument();
  });

  it("includes WITHDRAWN as a status option and setting it saves successfully", async () => {
    (updateProperty as jest.Mock).mockResolvedValue({ ...PROPERTY, status: "WITHDRAWN" });
    render(<PropertyEditView id="abc" />);
    await waitFor(() => expect(screen.getByLabelText(/reference code/i)).toHaveValue("P-001"));

    fireEvent.change(screen.getByLabelText(/^status/i), { target: { value: "WITHDRAWN" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(updateProperty).toHaveBeenCalledWith("abc", { status: "WITHDRAWN" }));
    expect(await screen.findByText(/saved/i)).toBeInTheDocument();
  });

  it("lets an additional (non-Catalan) translation be added and saved", async () => {
    (addTranslation as jest.Mock).mockResolvedValue(undefined);
    render(<PropertyEditView id="abc" />);
    await waitFor(() => expect(screen.getByLabelText(/reference code/i)).toHaveValue("P-001"));

    fireEvent.change(screen.getByLabelText(/^locale/i), { target: { value: "en" } });
    fireEvent.change(screen.getByLabelText(/^title/i), { target: { value: "English title" } });
    fireEvent.click(screen.getByRole("button", { name: /add translation/i }));

    await waitFor(() =>
        expect(addTranslation).toHaveBeenCalledWith("abc", "en", "English title", undefined));
  });

  it("renders the PhotoUploader with the property's existing media", async () => {
    render(<PropertyEditView id="abc" />);

    await waitFor(() => expect(screen.getByTestId("photo-uploader")).toBeInTheDocument());
    expect(screen.getByTestId("photo-uploader")).toHaveTextContent("abc");
    expect(screen.getByTestId("photo-uploader")).toHaveTextContent("1 photo(s)");
  });
});
