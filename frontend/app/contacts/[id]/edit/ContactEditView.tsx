"use client";

import { useEffect, useState } from "react";
import {
  ContactDetail,
  ContactSource,
  ContactType,
  fetchContact,
  UpdateContactInput,
  updateContact,
} from "../../../../lib/contacts";
import { fetchUserDirectory, UserDirectoryEntry } from "../../../../lib/users";

const CONTACT_TYPES: ContactType[] = ["BUYER", "SELLER", "TENANT", "LANDLORD", "LEAD"];
const CONTACT_SOURCES: ContactSource[] = ["PORTAL", "WEBSITE", "REFERRAL", "MANUAL", "WALK_IN"];

type FieldKey = keyof UpdateContactInput;

export default function ContactEditView({ id }: { id: string }) {
  const [contact, setContact] = useState<ContactDetail | null>(null);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [fields, setFields] = useState<UpdateContactInput>({});
  const [changed, setChanged] = useState<Partial<Record<FieldKey, true>>>({});
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchContact(id).then((loaded) => {
      setContact(loaded);
      setFields({
        contactType: loaded.contactType,
        firstName: loaded.firstName,
        lastName: loaded.lastName,
        email: loaded.email ?? undefined,
        phone: loaded.phone ?? undefined,
        preferredLocale: loaded.preferredLocale ?? undefined,
        source: loaded.source,
        sourceDetail: loaded.sourceDetail ?? undefined,
        notes: loaded.notes ?? undefined,
        ownerUserId: loaded.ownerUserId,
      });
    });
    fetchUserDirectory().then(setUsers);
  }, [id]);

  function setField<K extends FieldKey>(key: K, value: UpdateContactInput[K]) {
    setFields((previous) => ({ ...previous, [key]: value }));
    setChanged((previous) => ({ ...previous, [key]: true }));
    setSaved(false);
  }

  async function handleSave(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSaved(false);

    const changedFields = Object.keys(changed) as FieldKey[];
    const payload = Object.fromEntries(changedFields.map((key) => [key, fields[key]])) as UpdateContactInput;

    try {
      const updated = await updateContact(id, payload);
      setContact(updated);
      setChanged({});
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the contact.");
    }
  }

  if (!contact) {
    return null;
  }

  return (
    <main>
      <h1>
        Edit {contact.firstName} {contact.lastName}
      </h1>

      <form onSubmit={handleSave}>
        <label htmlFor="firstName">First name</label>
        <input
          id="firstName"
          value={fields.firstName ?? ""}
          onChange={(event) => setField("firstName", event.target.value)}
        />

        <label htmlFor="lastName">Last name</label>
        <input
          id="lastName"
          value={fields.lastName ?? ""}
          onChange={(event) => setField("lastName", event.target.value)}
        />

        <label htmlFor="contactType">Contact type</label>
        <select
          id="contactType"
          value={fields.contactType}
          onChange={(event) => setField("contactType", event.target.value as ContactType)}
        >
          {CONTACT_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>

        <label htmlFor="source">Source</label>
        <select
          id="source"
          value={fields.source}
          onChange={(event) => setField("source", event.target.value as ContactSource)}
        >
          {CONTACT_SOURCES.map((candidate) => (
            <option key={candidate} value={candidate}>
              {candidate}
            </option>
          ))}
        </select>

        <label htmlFor="email">Email</label>
        <input id="email" value={fields.email ?? ""} onChange={(event) => setField("email", event.target.value)} />

        <label htmlFor="phone">Phone</label>
        <input id="phone" value={fields.phone ?? ""} onChange={(event) => setField("phone", event.target.value)} />

        <label htmlFor="sourceDetail">Source detail</label>
        <input
          id="sourceDetail"
          value={fields.sourceDetail ?? ""}
          onChange={(event) => setField("sourceDetail", event.target.value)}
        />

        <label htmlFor="notes">Notes</label>
        <textarea id="notes" value={fields.notes ?? ""} onChange={(event) => setField("notes", event.target.value)} />

        <label htmlFor="ownerUserId">Owner agent</label>
        <select
          id="ownerUserId"
          value={fields.ownerUserId ?? ""}
          onChange={(event) => setField("ownerUserId", event.target.value)}
        >
          {users.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.fullName}
            </option>
          ))}
        </select>

        <button type="submit">Save</button>

        {saved && <p>Saved.</p>}
        {error && <p>{error}</p>}
      </form>
    </main>
  );
}
