"use client";

import { useEffect, useState } from "react";
import { DealDetail, fetchDeal, UpdateDealInput, updateDeal } from "../../../../lib/deals";
import { fetchProperties, PropertySummary } from "../../../../lib/properties";
import { fetchUserDirectory, UserDirectoryEntry } from "../../../../lib/users";

// stage/coldReason/lostReason are deliberately not on this form - they are
// only ever changed through the deal detail page's stage select and
// cold/lost actions (see DealDetailView).
type FieldKey = keyof UpdateDealInput;

export default function DealEditView({ id }: { id: string }) {
  const [deal, setDeal] = useState<DealDetail | null>(null);
  const [properties, setProperties] = useState<PropertySummary[]>([]);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [fields, setFields] = useState<UpdateDealInput>({});
  const [changed, setChanged] = useState<Partial<Record<FieldKey, true>>>({});
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDeal(id).then((loaded) => {
      setDeal(loaded);
      setFields({
        propertyId: loaded.propertyId ?? undefined,
        assignedUserId: loaded.assignedUserId,
        expectedCloseDate: loaded.expectedCloseDate ?? undefined,
      });
    });
    fetchProperties().then(setProperties);
    fetchUserDirectory().then(setUsers);
  }, [id]);

  function setField<K extends FieldKey>(key: K, value: UpdateDealInput[K]) {
    setFields((previous) => ({ ...previous, [key]: value }));
    setChanged((previous) => ({ ...previous, [key]: true }));
    setSaved(false);
  }

  // Final-review fix: UpdateDealInput has no way to express "clear this
  // field" - omitting a key means "unchanged" to the backend's PATCH
  // handler, so clearing an already-set property/date used to save
  // nothing while the form still reported "Saved." Ignoring the attempt
  // (same convention PropertyEditView's setNumericField already uses for
  // a cleared number input) keeps the field showing its real value
  // instead of lying about having cleared it.
  function setOrIgnoreIfEmpty<K extends FieldKey>(key: K, rawValue: string) {
    if (rawValue === "") return;
    setField(key, rawValue as UpdateDealInput[K]);
  }

  async function handleSave(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSaved(false);

    const changedFields = Object.keys(changed) as FieldKey[];
    const payload = Object.fromEntries(changedFields.map((key) => [key, fields[key]])) as UpdateDealInput;

    try {
      const updated = await updateDeal(id, payload);
      setDeal(updated);
      setChanged({});
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the deal.");
    }
  }

  if (!deal) {
    return null;
  }

  return (
    <main>
      <h1>Edit deal for {deal.contactName}</h1>

      <form onSubmit={handleSave}>
        <label htmlFor="propertyId">Property</label>
        <select
          id="propertyId"
          value={fields.propertyId ?? ""}
          onChange={(event) => setOrIgnoreIfEmpty("propertyId", event.target.value)}
        >
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
          value={fields.assignedUserId ?? ""}
          onChange={(event) => setField("assignedUserId", event.target.value)}
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
          value={fields.expectedCloseDate ?? ""}
          onChange={(event) => setOrIgnoreIfEmpty("expectedCloseDate", event.target.value)}
        />

        <button type="submit">Save</button>

        {saved && <p>Saved.</p>}
        {error && <p>{error}</p>}
      </form>
    </main>
  );
}
