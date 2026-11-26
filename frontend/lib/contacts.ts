import { API_BASE_URL } from "./apiBaseUrl";
import { getCsrfToken } from "./auth";

export type ContactType = "BUYER" | "SELLER" | "TENANT" | "LANDLORD" | "LEAD";
export type ContactSource = "PORTAL" | "WEBSITE" | "REFERRAL" | "MANUAL" | "WALK_IN";

// Mirrors the backend's ContactResponse (see
// backend/.../contact/ContactResponse.java) field for field.
export type ContactSummary = {
  id: string;
  contactType: ContactType;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  preferredLocale: string | null;
  source: ContactSource;
  sourceDetail: string | null;
  notes: string | null;
  ownerUserId: string;
  createdAt: string;
  updatedAt: string;
};

export async function fetchContacts(): Promise<ContactSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/contacts`, { credentials: "include" });
  return response.json();
}

// Mirrors the backend's CreateContactRequest fields exactly (see
// backend/.../contact/CreateContactRequest.java). Only contactType,
// firstName, lastName, and source are required there; everything else is
// optional here too.
export type CreateContactInput = {
  contactType: ContactType;
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  preferredLocale?: string;
  source: ContactSource;
  sourceDetail?: string;
  notes?: string;
  ownerUserId?: string;
};

export async function createContact(data: CreateContactInput): Promise<{ id: string }> {
  const response = await fetch(`${API_BASE_URL}/api/contacts`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error("Could not create the contact.");
  }
  return response.json();
}
