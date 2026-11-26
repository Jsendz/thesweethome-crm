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
