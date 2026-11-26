"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "../../lib/AuthContext";
import { fetchProperties, PropertySummary } from "../../lib/properties";

export default function PropertiesPage() {
  const { user, loading } = useAuth();
  const [properties, setProperties] = useState<PropertySummary[]>([]);

  useEffect(() => {
    if (loading) return;
    if (!user) return; // AuthProvider redirects to /login for this case.
    fetchProperties().then(setProperties);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, user]);

  if (loading || !user) {
    return null;
  }

  return (
    <main>
      <h1>Listings</h1>
      <Link href="/properties/new">New listing</Link>

      {properties.length === 0 ? (
        <p>No listings yet.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Reference</th>
              <th>Type</th>
              <th>Status</th>
              <th>Price</th>
              <th>Photo</th>
            </tr>
          </thead>
          <tbody>
            {properties.map((property) => (
              <tr key={property.id}>
                <td>{property.referenceCode}</td>
                <td>
                  {property.propertyType} / {property.operation}
                </td>
                <td>{property.status}</td>
                <td>{property.price}</td>
                <td>
                  {property.thumbnailUrl && (
                    <img src={property.thumbnailUrl} alt={property.referenceCode} />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
