"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "../../../lib/AuthContext";
import { createContact, ContactSource, ContactType } from "../../../lib/contacts";
import { fetchUserDirectory, UserDirectoryEntry } from "../../../lib/users";

const CONTACT_TYPES: ContactType[] = ["BUYER", "SELLER", "TENANT", "LANDLORD", "LEAD"];
const CONTACT_SOURCES: ContactSource[] = ["PORTAL", "WEBSITE", "REFERRAL", "MANUAL", "WALK_IN"];

export default function NewContactPage() {
  const router = useRouter();
  const { user } = useAuth();

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [contactType, setContactType] = useState<ContactType>("LEAD");
  const [source, setSource] = useState<ContactSource>("MANUAL");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [sourceDetail, setSourceDetail] = useState("");
  const [notes, setNotes] = useState("");
  const [ownerUserId, setOwnerUserId] = useState("");
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchUserDirectory().then(setUsers);
  }, []);

  // Defaults the owner-agent select to the logged-in user once both the
  // user and the directory are available - whichever resolves second.
  useEffect(() => {
    if (user && !ownerUserId) {
      setOwnerUserId(user.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, users]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    // Mirrors ContactController's own required-field check, so the user
    // sees the problem before a round trip instead of after a 400.
    if (!firstName.trim() || !lastName.trim() || !contactType || !source) {
      setError("First name, last name, contact type, and source are required.");
      return;
    }

    try {
      const { id } = await createContact({
        contactType,
        firstName,
        lastName,
        email: email || undefined,
        phone: phone || undefined,
        source,
        sourceDetail: sourceDetail || undefined,
        notes: notes || undefined,
        ownerUserId: ownerUserId || undefined,
      });
      router.push(`/contacts/${id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the contact.");
    }
  }

  return (
    <main>
      <h1>New contact</h1>
      <form onSubmit={handleSubmit}>
        <label htmlFor="firstName">First name</label>
        <input id="firstName" value={firstName} onChange={(event) => setFirstName(event.target.value)} />

        <label htmlFor="lastName">Last name</label>
        <input id="lastName" value={lastName} onChange={(event) => setLastName(event.target.value)} />

        <label htmlFor="contactType">Contact type</label>
        <select
          id="contactType"
          value={contactType}
          onChange={(event) => setContactType(event.target.value as ContactType)}
        >
          {CONTACT_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>

        <label htmlFor="source">Source</label>
        <select id="source" value={source} onChange={(event) => setSource(event.target.value as ContactSource)}>
          {CONTACT_SOURCES.map((candidate) => (
            <option key={candidate} value={candidate}>
              {candidate}
            </option>
          ))}
        </select>

        <label htmlFor="email">Email</label>
        <input id="email" value={email} onChange={(event) => setEmail(event.target.value)} />

        <label htmlFor="phone">Phone</label>
        <input id="phone" value={phone} onChange={(event) => setPhone(event.target.value)} />

        <label htmlFor="sourceDetail">Source detail</label>
        <input id="sourceDetail" value={sourceDetail} onChange={(event) => setSourceDetail(event.target.value)} />

        <label htmlFor="notes">Notes</label>
        <textarea id="notes" value={notes} onChange={(event) => setNotes(event.target.value)} />

        <label htmlFor="ownerUserId">Owner agent</label>
        <select id="ownerUserId" value={ownerUserId} onChange={(event) => setOwnerUserId(event.target.value)}>
          {users.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.fullName}
            </option>
          ))}
        </select>

        <button type="submit">Create contact</button>

        {error && <p>{error}</p>}
      </form>
    </main>
  );
}
