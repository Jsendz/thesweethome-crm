"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ActivityEntry,
  ActivityStatus,
  ActivityType,
  DEAL_STAGES,
  DealDetail,
  DealStage,
  fetchActivities,
  fetchDeal,
  logActivity,
  updateDeal,
} from "../../../lib/deals";

const ACTIVITY_TYPES: ActivityType[] = ["CALL", "EMAIL", "VIEWING", "MEETING", "NOTE", "WHATSAPP"];
const ACTIVITY_STATUSES: ActivityStatus[] = ["SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"];

// Stages that require a reason collected via the dedicated Mark as
// cold/Close as lost buttons below, rather than the plain stage dropdown,
// which has no way to collect one.
const STAGES_REQUIRING_A_REASON: DealStage[] = ["COLD", "CLOSED_LOST"];

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function nowForDateTimeInput(): string {
  // yyyy-MM-ddThh:mm, the format <input type="datetime-local"> expects and
  // displays in LOCAL time. Date#toISOString() is always UTC, so building
  // this from it (the original implementation) silently shifted the
  // default by the browser's UTC offset - every freshly-logged activity in
  // Andorra (UTC+1/+2) would have been saved 1-2 hours into the past.
  const now = new Date();
  return (
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` +
    `T${pad(now.getHours())}:${pad(now.getMinutes())}`
  );
}

function formatOccurredAt(occurredAt: string): string {
  return new Date(occurredAt).toLocaleString();
}

function sortedByOccurredAtDescending(entries: ActivityEntry[]): ActivityEntry[] {
  return [...entries].sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime());
}

export default function DealDetailView({ id }: { id: string }) {
  const [deal, setDeal] = useState<DealDetail | null>(null);
  const [activities, setActivities] = useState<ActivityEntry[]>([]);

  const [activityType, setActivityType] = useState<ActivityType>("CALL");
  const [notes, setNotes] = useState("");
  const [occurredAt, setOccurredAt] = useState(nowForDateTimeInput());
  const [status, setStatus] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDeal(id).then(setDeal);
    fetchActivities(id).then((loaded) => setActivities(sortedByOccurredAtDescending(loaded)));
  }, [id]);

  async function handleStageChange(newStage: DealStage) {
    setError(null);
    try {
      const updated = await updateDeal(id, { stage: newStage });
      setDeal(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the deal.");
    }
  }

  async function handleMarkAsCold() {
    const reason = window.prompt("Reason for marking this deal as cold:");
    if (!reason || !reason.trim()) return;
    setError(null);
    try {
      const updated = await updateDeal(id, { stage: "COLD", coldReason: reason });
      setDeal(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the deal.");
    }
  }

  async function handleCloseAsLost() {
    const reason = window.prompt("Reason for closing this deal as lost:");
    if (!reason || !reason.trim()) return;
    setError(null);
    try {
      const updated = await updateDeal(id, { stage: "CLOSED_LOST", lostReason: reason });
      setDeal(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the deal.");
    }
  }

  async function handleLogActivity(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const logged = await logActivity(id, {
        activityType,
        notes: notes || undefined,
        occurredAt: new Date(occurredAt).toISOString(),
        status: status ? (status as ActivityStatus) : undefined,
      });
      setActivities((previous) => sortedByOccurredAtDescending([...previous, logged]));
      setNotes("");
      setStatus("");
      setOccurredAt(nowForDateTimeInput());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not log the activity.");
    }
  }

  if (!deal) {
    return null;
  }

  return (
    <main>
      <h1>
        <Link href={`/contacts/${deal.contactId}`}>{deal.contactName}</Link>
      </h1>
      <Link href={`/deals/${deal.id}/edit`}>Edit</Link>

      <dl>
        <dt>Property</dt>
        <dd>{deal.propertyReferenceCode}</dd>
        <dt>Assigned agent</dt>
        <dd>{deal.assignedUserFullName}</dd>
        <dt>Expected close date</dt>
        <dd>{deal.expectedCloseDate}</dd>
        {deal.coldReason && (
          <>
            <dt>Cold reason</dt>
            <dd>{deal.coldReason}</dd>
          </>
        )}
        {deal.lostReason && (
          <>
            <dt>Lost reason</dt>
            <dd>{deal.lostReason}</dd>
          </>
        )}
      </dl>

      <label htmlFor="stage">Stage</label>
      <select
        id="stage"
        value={deal.stage}
        onChange={(event) => handleStageChange(event.target.value as DealStage)}
      >
        {DEAL_STAGES.map((candidate) => (
          <option
            key={candidate}
            value={candidate}
            disabled={STAGES_REQUIRING_A_REASON.includes(candidate) && deal.stage !== candidate}
          >
            {candidate}
          </option>
        ))}
      </select>

      <button type="button" onClick={handleMarkAsCold}>
        Mark as cold
      </button>
      <button type="button" onClick={handleCloseAsLost}>
        Close as lost
      </button>

      {error && <p>{error}</p>}

      <section>
        <h2>Activity</h2>
        <form onSubmit={handleLogActivity}>
          <label htmlFor="activityType">Activity type</label>
          <select
            id="activityType"
            value={activityType}
            onChange={(event) => setActivityType(event.target.value as ActivityType)}
          >
            {ACTIVITY_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>

          <label htmlFor="notes">Notes</label>
          <textarea id="notes" value={notes} onChange={(event) => setNotes(event.target.value)} />

          <label htmlFor="occurredAt">Occurred at</label>
          <input
            id="occurredAt"
            type="datetime-local"
            value={occurredAt}
            onChange={(event) => setOccurredAt(event.target.value)}
          />

          <label htmlFor="status">Status</label>
          <select id="status" value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">Default</option>
            {ACTIVITY_STATUSES.map((candidate) => (
              <option key={candidate} value={candidate}>
                {candidate}
              </option>
            ))}
          </select>

          <button type="submit">Log activity</button>
        </form>

        <ul>
          {activities.map((activity) => (
            <li key={activity.id}>
              <span>{formatOccurredAt(activity.occurredAt)}</span>
              {" — "}
              <span>{activity.activityType}</span>
              {" — "}
              <span>{activity.status}</span>
              {" — "}
              <span>{activity.userFullName}</span>
              {activity.notes && (
                <>
                  {" — "}
                  <span>{activity.notes}</span>
                </>
              )}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
