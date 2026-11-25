"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchProperty, PropertyDetail } from "../../../lib/properties";

// Prefers the jpeg variant of a given size, falling back to webp - matches
// the same preference order PropertySummaryResponse uses on the backend for
// its own thumbnailUrl.
function pickVariantUrl(variants: Record<string, string>, size: string): string | null {
  return variants[`${size}_jpeg`] ?? variants[`${size}_webp`] ?? null;
}

export default function PropertyDetailView({ id }: { id: string }) {
  const [property, setProperty] = useState<PropertyDetail | null>(null);

  useEffect(() => {
    fetchProperty(id).then(setProperty);
  }, [id]);

  if (!property) {
    return null;
  }

  const caTranslation = property.translations.find((t) => t.locale === "ca");
  const otherTranslations = property.translations.filter((t) => t.locale !== "ca");

  return (
    <main>
      <h1>{property.referenceCode}</h1>
      <Link href={`/properties/${property.id}/edit`}>Edit</Link>

      <dl>
        <dt>Property type</dt>
        <dd>{property.propertyType}</dd>
        <dt>Operation</dt>
        <dd>{property.operation}</dd>
        <dt>Status</dt>
        <dd>{property.status}</dd>
        <dt>Address</dt>
        <dd>{property.addressLine}</dd>
        <dt>Parish</dt>
        <dd>{property.parish}</dd>
        <dt>Price</dt>
        <dd>{property.price}</dd>
        <dt>Currency</dt>
        <dd>{property.currency}</dd>
        <dt>Area (m²)</dt>
        <dd>{property.areaM2}</dd>
        <dt>Rooms</dt>
        <dd>{property.rooms}</dd>
        <dt>Bathrooms</dt>
        <dd>{property.bathrooms}</dd>
        <dt>Year built</dt>
        <dd>{property.yearBuilt}</dd>
        <dt>Energy rating</dt>
        <dd>{property.energyRating}</dd>
        <dt>Exclusive</dt>
        <dd>{property.isExclusive ? "Yes" : "No"}</dd>
      </dl>

      {caTranslation && (
        <section>
          <h2>{caTranslation.title}</h2>
          {caTranslation.description && <p>{caTranslation.description}</p>}
        </section>
      )}

      {otherTranslations.length > 0 && (
        <section>
          <h2>Other translations</h2>
          {otherTranslations.map((translation) => (
            <article key={translation.locale}>
              <h3>{translation.locale}</h3>
              <p>{translation.title}</p>
              {translation.description && <p>{translation.description}</p>}
            </article>
          ))}
        </section>
      )}

      {property.media.length > 0 && (
        <section>
          <h2>Photos</h2>
          <ul>
            {property.media.map((media) => {
              const url = pickVariantUrl(media.variants, "card") ?? pickVariantUrl(media.variants, "gallery");
              return (
                <li key={media.id}>{url && <img src={url} alt={property.referenceCode} />}</li>
              );
            })}
          </ul>
        </section>
      )}
    </main>
  );
}
