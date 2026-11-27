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

// Mirrors the backend's ContactResponse exactly - same shape as
// ContactSummary, named separately for readability at call sites that deal
// with a single contact rather than a list.
export type ContactDetail = ContactSummary;

export async function fetchContact(id: string): Promise<ContactDetail> {
  const response = await fetch(`${API_BASE_URL}/api/contacts/${id}`, { credentials: "include" });
  return response.json();
}

// Mirrors the backend's UpdateContactRequest: every field is optional,
// same PATCH semantics as UpdatePropertyInput.
export type UpdateContactInput = Partial<CreateContactInput>;

export async function updateContact(id: string, data: UpdateContactInput): Promise<ContactDetail> {
  const response = await fetch(`${API_BASE_URL}/api/contacts/${id}`, {
    method: "PATCH",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error("Could not save the contact.");
  }
  return response.json();
}

// A minimal local shape good enough for the contact detail page's deals
// list - Task 9 defines the fuller DealSummary type the pipeline list page
// uses, which this type should be widened to match if it turns out not to
// already.
export type DealSummary = {
  id: string;
  stage: string;
  propertyReferenceCode: string | null;
  assignedUserFullName: string;
};

export async function fetchDealsForContact(contactId: string): Promise<DealSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/deals?contactId=${contactId}`, { credentials: "include" });
  return response.json();
}
