"use client";

import { useEffect, useState } from "react";
import PhotoUploader from "../../../components/PhotoUploader";
import {
  addTranslation,
  fetchProperty,
  Operation,
  PropertyDetail,
  PropertyStatus,
  PropertyType,
  updateProperty,
  UpdatePropertyInput,
} from "../../../../lib/properties";

const PROPERTY_TYPES: PropertyType[] = ["APARTMENT", "HOUSE", "LAND", "COMMERCIAL", "PARKING", "OTHER"];
const OPERATIONS: Operation[] = ["SALE", "RENT", "SALE_OR_RENT"];
const STATUSES: PropertyStatus[] = [
  "DRAFT",
  "ACTIVE",
  "RESERVED",
  "UNDER_OFFER",
  "SOLD",
  "RENTED",
  "WITHDRAWN",
];
const TRANSLATABLE_LOCALES = ["es", "fr", "en"] as const;

type FieldKey = keyof UpdatePropertyInput;

export default function PropertyEditView({ id }: { id: string }) {
  const [property, setProperty] = useState<PropertyDetail | null>(null);
  const [fields, setFields] = useState<UpdatePropertyInput>({});
  const [changed, setChanged] = useState<Partial<Record<FieldKey, true>>>({});
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [newLocale, setNewLocale] = useState<(typeof TRANSLATABLE_LOCALES)[number]>("es");
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");

  useEffect(() => {
    fetchProperty(id).then((loaded) => {
      setProperty(loaded);
      const ca = loaded.translations.find((t) => t.locale === "ca");
      setFields({
        referenceCode: loaded.referenceCode,
        propertyType: loaded.propertyType,
        operation: loaded.operation,
        status: loaded.status,
        addressLine: loaded.addressLine ?? undefined,
        parish: loaded.parish ?? undefined,
        latitude: loaded.latitude ?? undefined,
        longitude: loaded.longitude ?? undefined,
        price: loaded.price,
        areaM2: loaded.areaM2 ?? undefined,
        rooms: loaded.rooms ?? undefined,
        bathrooms: loaded.bathrooms ?? undefined,
        yearBuilt: loaded.yearBuilt ?? undefined,
        energyRating: loaded.energyRating ?? undefined,
        isExclusive: loaded.isExclusive,
        exclusivityEndDate: loaded.exclusivityEndDate ?? undefined,
        caTitle: ca?.title,
        caDescription: ca?.description ?? undefined,
      });
    });
  }, [id]);

  function setField<K extends FieldKey>(key: K, value: UpdatePropertyInput[K]) {
    setFields((previous) => ({ ...previous, [key]: value }));
    setChanged((previous) => ({ ...previous, [key]: true }));
    setSaved(false);
  }

  async function handleSave(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSaved(false);

    const changedFields = Object.keys(changed) as FieldKey[];
    const payload = Object.fromEntries(
        changedFields.map((key) => [key, fields[key]])) as UpdatePropertyInput;

    try {
      const updated = await updateProperty(id, payload);
      setProperty(updated);
      setChanged({});
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the listing.");
    }
  }

  async function handleAddTranslation(event: React.FormEvent) {
    event.preventDefault();
    if (!newTitle.trim()) return;
    await addTranslation(id, newLocale, newTitle, newDescription || undefined);
    setProperty((previous) =>
        previous
            ? {
                ...previous,
                translations: [
                  ...previous.translations.filter((t) => t.locale !== newLocale),
                  { locale: newLocale, title: newTitle, description: newDescription || null },
                ],
              }
            : previous);
    setNewTitle("");
    setNewDescription("");
  }

  if (!property) {
    return null;
  }

  return (
    <main>
      <h1>Edit {property.referenceCode}</h1>

      <form onSubmit={handleSave}>
        <label htmlFor="referenceCode">Reference code</label>
        <input
          id="referenceCode"
          value={fields.referenceCode ?? ""}
          onChange={(event) => setField("referenceCode", event.target.value)}
        />

        <label htmlFor="propertyType">Property type</label>
        <select
          id="propertyType"
          value={fields.propertyType}
          onChange={(event) => setField("propertyType", event.target.value as PropertyType)}
        >
          {PROPERTY_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>

        <label htmlFor="operation">Operation</label>
        <select
          id="operation"
          value={fields.operation}
          onChange={(event) => setField("operation", event.target.value as Operation)}
        >
          {OPERATIONS.map((op) => (
            <option key={op} value={op}>
              {op}
            </option>
          ))}
        </select>

        <label htmlFor="status">Status</label>
        <select
          id="status"
          value={fields.status}
          onChange={(event) => setField("status", event.target.value as PropertyStatus)}
        >
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>

        <label htmlFor="addressLine">Address</label>
        <input
          id="addressLine"
          value={fields.addressLine ?? ""}
          onChange={(event) => setField("addressLine", event.target.value)}
        />

        <label htmlFor="parish">Parish</label>
        <input id="parish" value={fields.parish ?? ""} onChange={(event) => setField("parish", event.target.value)} />

        <label htmlFor="price">Price</label>
        <input
          id="price"
          type="number"
          value={fields.price ?? ""}
          onChange={(event) => setField("price", Number(event.target.value))}
        />

        <label htmlFor="areaM2">Area (m²)</label>
        <input
          id="areaM2"
          type="number"
          value={fields.areaM2 ?? ""}
          onChange={(event) => setField("areaM2", Number(event.target.value))}
        />

        <label htmlFor="rooms">Rooms</label>
        <input
          id="rooms"
          type="number"
          value={fields.rooms ?? ""}
          onChange={(event) => setField("rooms", Number(event.target.value))}
        />

        <label htmlFor="bathrooms">Bathrooms</label>
        <input
          id="bathrooms"
          type="number"
          value={fields.bathrooms ?? ""}
          onChange={(event) => setField("bathrooms", Number(event.target.value))}
        />

        <label htmlFor="yearBuilt">Year built</label>
        <input
          id="yearBuilt"
          type="number"
          value={fields.yearBuilt ?? ""}
          onChange={(event) => setField("yearBuilt", Number(event.target.value))}
        />

        <label htmlFor="energyRating">Energy rating</label>
        <input
          id="energyRating"
          value={fields.energyRating ?? ""}
          onChange={(event) => setField("energyRating", event.target.value)}
        />

        <label htmlFor="isExclusive">Exclusive</label>
        <input
          id="isExclusive"
          type="checkbox"
          checked={fields.isExclusive ?? false}
          onChange={(event) => setField("isExclusive", event.target.checked)}
        />

        <label htmlFor="exclusivityEndDate">Exclusivity end date</label>
        <input
          id="exclusivityEndDate"
          type="date"
          value={fields.exclusivityEndDate ?? ""}
          onChange={(event) => setField("exclusivityEndDate", event.target.value)}
        />

        <label htmlFor="caTitle">Catalan title</label>
        <input id="caTitle" value={fields.caTitle ?? ""} onChange={(event) => setField("caTitle", event.target.value)} />

        <label htmlFor="caDescription">Catalan description</label>
        <textarea
          id="caDescription"
          value={fields.caDescription ?? ""}
          onChange={(event) => setField("caDescription", event.target.value)}
        />

        <button type="submit">Save</button>

        {saved && <p>Saved.</p>}
        {error && <p>{error}</p>}
      </form>

      <section>
        <h2>Add a translation</h2>
        <form onSubmit={handleAddTranslation}>
          <label htmlFor="locale">Locale</label>
          <select
            id="locale"
            value={newLocale}
            onChange={(event) => setNewLocale(event.target.value as (typeof TRANSLATABLE_LOCALES)[number])}
          >
            {TRANSLATABLE_LOCALES.map((locale) => (
              <option key={locale} value={locale}>
                {locale}
              </option>
            ))}
          </select>

          <label htmlFor="title">Title</label>
          <input id="title" value={newTitle} onChange={(event) => setNewTitle(event.target.value)} />

          <label htmlFor="description">Description</label>
          <textarea
            id="description"
            value={newDescription}
            onChange={(event) => setNewDescription(event.target.value)}
          />

          <button type="submit">Add translation</button>
        </form>
      </section>

      <section>
        <h2>Photos</h2>
        <PhotoUploader propertyId={property.id} media={property.media} />
      </section>
    </main>
  );
}
