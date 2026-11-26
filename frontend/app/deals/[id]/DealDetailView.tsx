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

function nowForDateTimeInput(): string {
  // yyyy-MM-ddThh:mm, the format <input type="datetime-local"> expects.
  return new Date().toISOString().slice(0, 16);
}

export default function DealDetailView({ id }: { id: string }) {
  const [deal, setDeal] = useState<DealDetail | null>(null);
  const [activities, setActivities] = useState<ActivityEntry[]>([]);

  const [activityType, setActivityType] = useState<ActivityType>("CALL");
  const [notes, setNotes] = useState("");
  const [occurredAt, setOccurredAt] = useState(nowForDateTimeInput());
  const [status, setStatus] = useState("");

  useEffect(() => {
    fetchDeal(id).then(setDeal);
    fetchActivities(id).then(setActivities);
  }, [id]);

  async function handleStageChange(newStage: DealStage) {
    const updated = await updateDeal(id, { stage: newStage });
    setDeal(updated);
  }

  async function handleMarkAsCold() {
    const reason = window.prompt("Reason for marking this deal as cold:");
    if (!reason || !reason.trim()) return;
    const updated = await updateDeal(id, { stage: "COLD", coldReason: reason });
    setDeal(updated);
  }

  async function handleCloseAsLost() {
    const reason = window.prompt("Reason for closing this deal as lost:");
    if (!reason || !reason.trim()) return;
    const updated = await updateDeal(id, { stage: "CLOSED_LOST", lostReason: reason });
    setDeal(updated);
  }

  async function handleLogActivity(event: React.FormEvent) {
    event.preventDefault();
    const logged = await logActivity(id, {
      activityType,
      notes: notes || undefined,
      occurredAt: new Date(occurredAt).toISOString(),
      status: status ? (status as ActivityStatus) : undefined,
    });
    setActivities((previous) => [logged, ...previous]);
    setNotes("");
    setStatus("");
  }

  if (!deal) {
    return null;
  }

  return (
    <main>
      <h1>{deal.contactName}</h1>
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
          <option key={candidate} value={candidate}>
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
              <span>{activity.activityType}</span>
              {" — "}
              <span>{activity.status}</span>
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
