import { API_BASE_URL } from "./apiBaseUrl";
import { getCsrfToken } from "./auth";

export type PropertyType = "APARTMENT" | "HOUSE" | "LAND" | "COMMERCIAL" | "PARKING" | "OTHER";
export type Operation = "SALE" | "RENT" | "SALE_OR_RENT";
export type PropertyStatus =
  | "DRAFT"
  | "ACTIVE"
  | "RESERVED"
  | "UNDER_OFFER"
  | "SOLD"
  | "RENTED"
  | "WITHDRAWN";

export type PropertySummary = {
  id: string;
  referenceCode: string;
  propertyType: PropertyType;
  operation: Operation;
  status: PropertyStatus;
  price: number;
  thumbnailUrl: string | null;
};

export async function fetchProperties(): Promise<PropertySummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/properties`, { credentials: "include" });
  return response.json();
}

// Mirrors the backend's CreatePropertyRequest fields exactly (see
// backend/.../property/CreatePropertyRequest.java). Only referenceCode,
// propertyType, operation, price, and caTitle are required there; everything
// else is optional here too.
export type CreatePropertyInput = {
  referenceCode: string;
  propertyType: PropertyType;
  operation: Operation;
  addressLine?: string;
  parish?: string;
  latitude?: number;
  longitude?: number;
  price: number;
  areaM2?: number;
  rooms?: number;
  bathrooms?: number;
  yearBuilt?: number;
  energyRating?: string;
  isExclusive?: boolean;
  exclusivityEndDate?: string;
  caTitle: string;
  caDescription?: string;
};

export async function createProperty(data: CreatePropertyInput): Promise<{ id: string }> {
  const response = await fetch(`${API_BASE_URL}/api/properties`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    if (response.status === 409) {
      throw new Error("This reference code is already in use.");
    }
    throw new Error("Could not create the listing.");
  }
  return response.json();
}
