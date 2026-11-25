import { API_BASE_URL } from "./apiBaseUrl";

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
