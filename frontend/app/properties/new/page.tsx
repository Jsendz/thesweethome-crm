"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createProperty, Operation, PropertyType } from "../../../lib/properties";

const PROPERTY_TYPES: PropertyType[] = ["APARTMENT", "HOUSE", "LAND", "COMMERCIAL", "PARKING", "OTHER"];
const OPERATIONS: Operation[] = ["SALE", "RENT", "SALE_OR_RENT"];

export default function NewPropertyPage() {
  const router = useRouter();

  const [referenceCode, setReferenceCode] = useState("");
  const [propertyType, setPropertyType] = useState<PropertyType>("APARTMENT");
  const [operation, setOperation] = useState<Operation>("SALE");
  const [addressLine, setAddressLine] = useState("");
  const [parish, setParish] = useState("");
  const [price, setPrice] = useState("");
  const [areaM2, setAreaM2] = useState("");
  const [rooms, setRooms] = useState("");
  const [bathrooms, setBathrooms] = useState("");
  const [yearBuilt, setYearBuilt] = useState("");
  const [energyRating, setEnergyRating] = useState("");
  const [isExclusive, setIsExclusive] = useState(false);
  const [exclusivityEndDate, setExclusivityEndDate] = useState("");
  const [caTitle, setCaTitle] = useState("");
  const [caDescription, setCaDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    // Mirrors PropertyController's own required-field check, so the user
    // sees the problem before a round trip instead of after a 400.
    if (!referenceCode.trim() || !propertyType || !operation || !price.trim() || !caTitle.trim()) {
      setError("Reference code, property type, operation, price, and a Catalan title are required.");
      return;
    }

    try {
      const { id } = await createProperty({
        referenceCode,
        propertyType,
        operation,
        addressLine: addressLine || undefined,
        parish: parish || undefined,
        price: Number(price),
        areaM2: areaM2 ? Number(areaM2) : undefined,
        rooms: rooms ? Number(rooms) : undefined,
        bathrooms: bathrooms ? Number(bathrooms) : undefined,
        yearBuilt: yearBuilt ? Number(yearBuilt) : undefined,
        energyRating: energyRating || undefined,
        isExclusive,
        exclusivityEndDate: exclusivityEndDate || undefined,
        caTitle,
        caDescription: caDescription || undefined,
      });
      router.push(`/properties/${id}/edit`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the listing.");
    }
  }

  return (
    <main>
      <h1>New listing</h1>
      <form onSubmit={handleSubmit}>
        <label htmlFor="referenceCode">Reference code</label>
        <input
          id="referenceCode"
          value={referenceCode}
          onChange={(event) => setReferenceCode(event.target.value)}
        />

        <label htmlFor="propertyType">Property type</label>
        <select
          id="propertyType"
          value={propertyType}
          onChange={(event) => setPropertyType(event.target.value as PropertyType)}
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
          value={operation}
          onChange={(event) => setOperation(event.target.value as Operation)}
        >
          {OPERATIONS.map((op) => (
            <option key={op} value={op}>
              {op}
            </option>
          ))}
        </select>

        <label htmlFor="addressLine">Address</label>
        <input id="addressLine" value={addressLine} onChange={(event) => setAddressLine(event.target.value)} />

        <label htmlFor="parish">Parish</label>
        <input id="parish" value={parish} onChange={(event) => setParish(event.target.value)} />

        <label htmlFor="price">Price</label>
        <input id="price" type="number" value={price} onChange={(event) => setPrice(event.target.value)} />

        <label htmlFor="areaM2">Area (m²)</label>
        <input id="areaM2" type="number" value={areaM2} onChange={(event) => setAreaM2(event.target.value)} />

        <label htmlFor="rooms">Rooms</label>
        <input id="rooms" type="number" value={rooms} onChange={(event) => setRooms(event.target.value)} />

        <label htmlFor="bathrooms">Bathrooms</label>
        <input id="bathrooms" type="number" value={bathrooms} onChange={(event) => setBathrooms(event.target.value)} />

        <label htmlFor="yearBuilt">Year built</label>
        <input id="yearBuilt" type="number" value={yearBuilt} onChange={(event) => setYearBuilt(event.target.value)} />

        <label htmlFor="energyRating">Energy rating</label>
        <input id="energyRating" value={energyRating} onChange={(event) => setEnergyRating(event.target.value)} />

        <label htmlFor="isExclusive">Exclusive</label>
        <input
          id="isExclusive"
          type="checkbox"
          checked={isExclusive}
          onChange={(event) => setIsExclusive(event.target.checked)}
        />

        <label htmlFor="exclusivityEndDate">Exclusivity end date</label>
        <input
          id="exclusivityEndDate"
          type="date"
          value={exclusivityEndDate}
          onChange={(event) => setExclusivityEndDate(event.target.value)}
        />

        <label htmlFor="caTitle">Catalan title</label>
        <input id="caTitle" value={caTitle} onChange={(event) => setCaTitle(event.target.value)} />

        <label htmlFor="caDescription">Catalan description</label>
        <textarea
          id="caDescription"
          value={caDescription}
          onChange={(event) => setCaDescription(event.target.value)}
        />

        <button type="submit">Create listing</button>

        {error && <p>{error}</p>}
      </form>
    </main>
  );
}
