"use client";

import { useRef, useState } from "react";
import { deleteMedia, Media, reorderMedia, uploadMedia } from "../../lib/properties";

function thumbnailUrl(media: Media): string | null {
  return media.variants["thumbnail_jpeg"] ?? media.variants["thumbnail_webp"] ?? null;
}

export default function PhotoUploader({ propertyId, media }: { propertyId: string; media: Media[] }) {
  const [items, setItems] = useState<Media[]>(media);
  const [error, setError] = useState<string | null>(null);
  const draggedId = useRef<string | null>(null);

  async function handleFilesSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    if (files.length === 0) return;
    setError(null);
    try {
      const uploaded = await uploadMedia(propertyId, "PHOTO", files);
      setItems((previous) => [...previous, ...uploaded]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not upload the selected files.");
    } finally {
      event.target.value = "";
    }
  }

  async function handleDelete(mediaId: string) {
    await deleteMedia(propertyId, mediaId);
    setItems((previous) => previous.filter((item) => item.id !== mediaId));
  }

  function handleDragStart(event: React.DragEvent, mediaId: string) {
    draggedId.current = mediaId;
    event.dataTransfer.setData("text/plain", mediaId);
  }

  async function handleDrop(event: React.DragEvent, targetId: string) {
    event.preventDefault();
    const sourceId = event.dataTransfer.getData("text/plain") || draggedId.current;
    draggedId.current = null;
    if (!sourceId || sourceId === targetId) return;

    const currentOrder = items.map((item) => item.id);
    const fromIndex = currentOrder.indexOf(sourceId);
    const toIndex = currentOrder.indexOf(targetId);
    if (fromIndex === -1 || toIndex === -1) return;

    const reordered = [...currentOrder];
    reordered.splice(fromIndex, 1);
    reordered.splice(toIndex, 0, sourceId);

    setItems((previous) =>
        reordered.map((id, index) => ({ ...previous.find((item) => item.id === id)!, sortOrder: index })));
    await reorderMedia(propertyId, reordered);
  }

  return (
    <div>
      <label htmlFor="add-photos">Add photos</label>
      <input id="add-photos" type="file" multiple accept="image/*" onChange={handleFilesSelected} />

      {error && <p>{error}</p>}

      <ul>
        {items.map((item) => (
          <li
            key={item.id}
            data-testid={`photo-item-${item.id}`}
            draggable
            onDragStart={(event) => handleDragStart(event, item.id)}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => handleDrop(event, item.id)}
          >
            {thumbnailUrl(item) && <img src={thumbnailUrl(item)!} alt={`Photo ${item.id}`} />}
            <button type="button" onClick={() => handleDelete(item.id)}>
              Delete
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
