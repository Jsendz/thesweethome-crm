import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import PhotoUploader from "./PhotoUploader";
import { uploadMedia, deleteMedia, reorderMedia } from "../../lib/properties";

jest.mock("../../lib/properties");

const EXISTING_MEDIA = [
  {
    id: "m1",
    mediaType: "PHOTO" as const,
    variants: { thumbnail_jpeg: "http://x/m1-thumb.jpg", card_jpeg: "http://x/m1-card.jpg" },
    sortOrder: 0,
  },
  {
    id: "m2",
    mediaType: "PHOTO" as const,
    variants: { thumbnail_jpeg: "http://x/m2-thumb.jpg", card_jpeg: "http://x/m2-card.jpg" },
    sortOrder: 1,
  },
];

describe("PhotoUploader", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("uploads the selected files and shows the new thumbnails", async () => {
    (uploadMedia as jest.Mock).mockResolvedValue([
      { id: "m3", mediaType: "PHOTO", variants: { thumbnail_jpeg: "http://x/m3-thumb.jpg" }, sortOrder: 2 },
    ]);
    render(<PhotoUploader propertyId="p1" media={[]} />);

    const file = new File(["data"], "photo.jpg", { type: "image/jpeg" });
    fireEvent.change(screen.getByLabelText(/add photos/i), { target: { files: [file] } });

    await waitFor(() => expect(uploadMedia).toHaveBeenCalledWith("p1", "PHOTO", [file]));
    await waitFor(() => expect(screen.getByTestId("photo-item-m3")).toBeInTheDocument());
    expect(screen.getByAltText("Photo m3")).toHaveAttribute("src", "http://x/m3-thumb.jpg");
  });

  it("shows the server's error message when upload fails (e.g. a non-image file)", async () => {
    (uploadMedia as jest.Mock).mockRejectedValue(new Error("notes.txt is not a decodable image"));
    render(<PhotoUploader propertyId="p1" media={[]} />);

    const file = new File(["data"], "notes.txt", { type: "text/plain" });
    fireEvent.change(screen.getByLabelText(/add photos/i), { target: { files: [file] } });

    expect(await screen.findByText(/not a decodable image/i)).toBeInTheDocument();
  });

  it("removes a photo when its delete button is clicked", async () => {
    (deleteMedia as jest.Mock).mockResolvedValue(undefined);
    render(<PhotoUploader propertyId="p1" media={EXISTING_MEDIA} />);

    fireEvent.click(screen.getAllByRole("button", { name: /delete/i })[0]);

    await waitFor(() => expect(deleteMedia).toHaveBeenCalledWith("p1", "m1"));
    await waitFor(() => expect(screen.queryByTestId("photo-item-m1")).not.toBeInTheDocument());
  });

  it("reorders photos via drag-and-drop and persists the new order", async () => {
    (reorderMedia as jest.Mock).mockResolvedValue(undefined);
    render(<PhotoUploader propertyId="p1" media={EXISTING_MEDIA} />);

    const items = screen.getAllByTestId(/^photo-item-/);
    const dataTransfer = { setData: jest.fn(), getData: jest.fn(() => "m2") };
    fireEvent.dragStart(items[1], { dataTransfer });
    fireEvent.drop(items[0], { dataTransfer });

    await waitFor(() => expect(reorderMedia).toHaveBeenCalledWith("p1", ["m2", "m1"]));
  });
});
