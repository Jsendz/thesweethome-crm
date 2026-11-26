import { API_BASE_URL } from "./apiBaseUrl";
import { getCsrfToken } from "./auth";

// Mirrors the backend's CreateDealRequest fields exactly (see
// backend/.../deal/CreateDealRequest.java). Only contactId is required
// there; propertyId/assignedUserId/expectedCloseDate are optional. stage is
// always LEAD at creation and is not settable here.
export type CreateDealInput = {
  contactId: string;
  propertyId?: string;
  assignedUserId?: string;
  expectedCloseDate?: string;
};

// DealStage's declaration order (see backend/.../deal/DealStage.java) - the
// only ordering the spec implies, used both to sort the pipeline list and,
// via DEAL_STAGES.indexOf, as the sort key itself.
export const DEAL_STAGES = [
  "LEAD",
  "QUALIFIED",
  "VIEWING_SCHEDULED",
  "VIEWED",
  "OFFER_MADE",
  "NEGOTIATION",
  "UNDER_CONTRACT",
  "CLOSED_WON",
  "CLOSED_LOST",
  "COLD",
] as const;
export type DealStage = (typeof DEAL_STAGES)[number];

// Mirrors the backend's DealResponse (see backend/.../deal/DealResponse.java)
// field for field.
export type DealSummary = {
  id: string;
  contactId: string;
  contactName: string;
  propertyId: string | null;
  propertyReferenceCode: string | null;
  assignedUserId: string;
  assignedUserFullName: string;
  stage: DealStage;
  lostReason: string | null;
  coldReason: string | null;
  coldMarkedByUserId: string | null;
  coldMarkedAt: string | null;
  expectedCloseDate: string | null;
  createdAt: string;
  updatedAt: string;
};

export async function fetchDeals(): Promise<DealSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/deals`, { credentials: "include" });
  return response.json();
}

// Mirrors DealSummary - DealResponse is the same shape whether it came
// from the list or a single-deal fetch.
export type DealDetail = DealSummary;

export async function fetchDeal(id: string): Promise<DealDetail> {
  const response = await fetch(`${API_BASE_URL}/api/deals/${id}`, { credentials: "include" });
  return response.json();
}

// Mirrors the backend's UpdateDealRequest fields exactly (see
// backend/.../deal/UpdateDealRequest.java). Every field is optional, same
// PATCH semantics as UpdateContactInput/UpdatePropertyInput. stage=COLD and
// stage=CLOSED_LOST each require their own reason field in the same request
// (see DealController's special-cased validation).
export type UpdateDealInput = {
  propertyId?: string;
  assignedUserId?: string;
  stage?: DealStage;
  coldReason?: string;
  lostReason?: string;
  expectedCloseDate?: string;
};

export async function updateDeal(id: string, data: UpdateDealInput): Promise<DealDetail> {
  const response = await fetch(`${API_BASE_URL}/api/deals/${id}`, {
    method: "PATCH",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error("Could not save the deal.");
  }
  return response.json();
}

export type ActivityType = "CALL" | "EMAIL" | "VIEWING" | "MEETING" | "NOTE" | "WHATSAPP";
export type ActivityStatus = "SCHEDULED" | "COMPLETED" | "CANCELLED" | "NO_SHOW";

// Mirrors the backend's ActivityResponse (see
// backend/.../deal/ActivityResponse.java) field for field.
export type ActivityEntry = {
  id: string;
  dealId: string;
  contactId: string | null;
  propertyId: string | null;
  userId: string;
  userFullName: string;
  activityType: ActivityType;
  status: ActivityStatus;
  notes: string | null;
  occurredAt: string;
  createdAt: string;
};

export async function fetchActivities(dealId: string): Promise<ActivityEntry[]> {
  const response = await fetch(`${API_BASE_URL}/api/deals/${dealId}/activities`, { credentials: "include" });
  return response.json();
}

// Mirrors the backend's CreateActivityRequest fields exactly (see
// backend/.../deal/CreateActivityRequest.java). activityType/occurredAt are
// required there; status is optional - the backend defaults it based on
// activityType when omitted.
export type LogActivityInput = {
  activityType: ActivityType;
  notes?: string;
  occurredAt: string;
  status?: ActivityStatus;
};

export async function logActivity(dealId: string, data: LogActivityInput): Promise<ActivityEntry> {
  const response = await fetch(`${API_BASE_URL}/api/deals/${dealId}/activities`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error("Could not log the activity.");
  }
  return response.json();
}

export async function createDeal(data: CreateDealInput): Promise<{ id: string }> {
  const response = await fetch(`${API_BASE_URL}/api/deals`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error("Could not create the deal.");
  }
  return response.json();
}
