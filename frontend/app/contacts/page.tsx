"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "../../lib/AuthContext";
import { fetchContacts, ContactSummary } from "../../lib/contacts";
import { fetchUserDirectory, UserDirectoryEntry } from "../../lib/users";

export default function ContactsPage() {
  const { user, loading } = useAuth();
  const [contacts, setContacts] = useState<ContactSummary[]>([]);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);

  useEffect(() => {
    if (loading) return;
    if (!user) return; // AuthProvider redirects to /login for this case.
    fetchContacts().then(setContacts);
    fetchUserDirectory().then(setUsers);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, user]);

  if (loading || !user) {
    return null;
  }

  function ownerName(ownerUserId: string): string {
    return users.find((candidate) => candidate.id === ownerUserId)?.fullName ?? ownerUserId;
  }

  return (
    <main>
      <h1>Contacts</h1>
      <Link href="/contacts/new">New contact</Link>

      {contacts.length === 0 ? (
        <p>No contacts yet.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Type</th>
              <th>Source</th>
              <th>Phone</th>
              <th>Email</th>
              <th>Owner agent</th>
            </tr>
          </thead>
          <tbody>
            {contacts.map((contact) => (
              <tr key={contact.id}>
                <td>
                  <Link href={`/contacts/${contact.id}`}>
                    {contact.firstName} {contact.lastName}
                  </Link>
                </td>
                <td>{contact.contactType}</td>
                <td>{contact.source}</td>
                <td>{contact.phone}</td>
                <td>{contact.email}</td>
                <td>{ownerName(contact.ownerUserId)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
