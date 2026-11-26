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
