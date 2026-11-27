"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ContactDetail, DealSummary, fetchContact, fetchDealsForContact } from "../../../lib/contacts";
import { fetchUserDirectory, UserDirectoryEntry } from "../../../lib/users";

export default function ContactDetailView({ id }: { id: string }) {
  const [contact, setContact] = useState<ContactDetail | null>(null);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [deals, setDeals] = useState<DealSummary[]>([]);

  useEffect(() => {
    fetchContact(id).then(setContact);
    fetchUserDirectory().then(setUsers);
    fetchDealsForContact(id).then(setDeals);
  }, [id]);

  if (!contact) {
    return null;
  }

  const ownerName = users.find((candidate) => candidate.id === contact.ownerUserId)?.fullName ?? contact.ownerUserId;

  return (
    <main>
      <h1>
        {contact.firstName} {contact.lastName}
      </h1>
      <Link href={`/contacts/${contact.id}/edit`}>Edit</Link>
      <Link href={`/contacts/${contact.id}/deals/new`}>New deal</Link>

      <dl>
        <dt>Contact type</dt>
        <dd>{contact.contactType}</dd>
        <dt>Source</dt>
        <dd>{contact.source}</dd>
        <dt>Source detail</dt>
        <dd>{contact.sourceDetail}</dd>
        <dt>Email</dt>
        <dd>{contact.email}</dd>
        <dt>Phone</dt>
        <dd>{contact.phone}</dd>
        <dt>Preferred locale</dt>
        <dd>{contact.preferredLocale}</dd>
        <dt>Notes</dt>
        <dd>{contact.notes}</dd>
        <dt>Owner agent</dt>
        <dd>{ownerName}</dd>
      </dl>

      <section>
        <h2>Deals</h2>
        {deals.length === 0 ? (
          <p>No deals yet.</p>
        ) : (
          <ul>
            {deals.map((deal) => (
              <li key={deal.id}>
                <Link href={`/deals/${deal.id}`}>{deal.stage}</Link>
                {deal.propertyReferenceCode && (
                  <>
                    {" — "}
                    <span>{deal.propertyReferenceCode}</span>
                  </>
                )}
                {" — "}
                <span>{deal.assignedUserFullName}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
