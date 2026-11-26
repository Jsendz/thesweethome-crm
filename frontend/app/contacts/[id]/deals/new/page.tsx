"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "../../../../../lib/AuthContext";
import { createDeal } from "../../../../../lib/deals";
import { fetchProperties, PropertySummary } from "../../../../../lib/properties";
import { fetchUserDirectory, UserDirectoryEntry } from "../../../../../lib/users";

// This route reads its dynamic `id` via useParams() rather than the
// server-resolved `params` prop other dynamic pages here use (see
// properties/[id]/page.tsx) - the form itself needs no server-fetched data
// keyed by a promise, so there is nothing that needs the Suspense boundary
// cacheComponents: true otherwise requires around an awaited `params`.
export default function NewDealPage() {
  const { id: contactId } = useParams<{ id: string }>();
  const router = useRouter();
  const { user } = useAuth();

  const [properties, setProperties] = useState<PropertySummary[]>([]);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [propertyId, setPropertyId] = useState("");
  const [assignedUserId, setAssignedUserId] = useState("");
  const [expectedCloseDate, setExpectedCloseDate] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchProperties().then(setProperties);
    fetchUserDirectory().then(setUsers);
  }, []);

  useEffect(() => {
    if (user && !assignedUserId) {
      setAssignedUserId(user.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, users]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    try {
      const { id } = await createDeal({
        contactId,
        propertyId: propertyId || undefined,
        assignedUserId: assignedUserId || undefined,
        expectedCloseDate: expectedCloseDate || undefined,
      });
      router.push(`/deals/${id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the deal.");
    }
  }

  return (
    <main>
      <h1>New deal</h1>
      <form onSubmit={handleSubmit}>
        <label htmlFor="propertyId">Property</label>
        <select id="propertyId" value={propertyId} onChange={(event) => setPropertyId(event.target.value)}>
          <option value="">None</option>
          {properties.map((property) => (
            <option key={property.id} value={property.id}>
              {property.referenceCode}
            </option>
          ))}
        </select>

        <label htmlFor="assignedUserId">Assigned agent</label>
        <select
          id="assignedUserId"
          value={assignedUserId}
          onChange={(event) => setAssignedUserId(event.target.value)}
        >
          {users.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.fullName}
            </option>
          ))}
        </select>

        <label htmlFor="expectedCloseDate">Expected close date</label>
        <input
          id="expectedCloseDate"
          type="date"
          value={expectedCloseDate}
          onChange={(event) => setExpectedCloseDate(event.target.value)}
        />

        <button type="submit">Create deal</button>

        {error && <p>{error}</p>}
      </form>
    </main>
  );
}
