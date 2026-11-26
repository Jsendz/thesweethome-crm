"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "../../lib/AuthContext";
import { DEAL_STAGES, DealSummary, fetchDeals } from "../../lib/deals";

export default function DealsPage() {
  const { user, loading } = useAuth();
  const [deals, setDeals] = useState<DealSummary[]>([]);

  useEffect(() => {
    if (loading) return;
    if (!user) return; // AuthProvider redirects to /login for this case.
    fetchDeals().then(setDeals);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, user]);

  if (loading || !user) {
    return null;
  }

  const sortedDeals = [...deals].sort(
    (a, b) => DEAL_STAGES.indexOf(a.stage) - DEAL_STAGES.indexOf(b.stage),
  );

  return (
    <main>
      <h1>Deals</h1>

      {sortedDeals.length === 0 ? (
        <p>No deals yet.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Contact</th>
              <th>Property</th>
              <th>Stage</th>
              <th>Assigned agent</th>
              <th>Expected close date</th>
            </tr>
          </thead>
          <tbody>
            {sortedDeals.map((deal) => (
              <tr key={deal.id}>
                <td>
                  <Link href={`/deals/${deal.id}`}>{deal.contactName}</Link>
                </td>
                <td>{deal.propertyReferenceCode}</td>
                <td>{deal.stage}</td>
                <td>{deal.assignedUserFullName}</td>
                <td>{deal.expectedCloseDate}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
