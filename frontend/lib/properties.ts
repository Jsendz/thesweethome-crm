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

// Mirrors the backend's PropertyResponse (see
// backend/.../property/PropertyResponse.java) field for field.
export type Translation = {
  locale: string;
  title: string;
  description: string | null;
};

export type Media = {
  id: string;
  mediaType: "PHOTO" | "FLOORPLAN" | "VIDEO" | "VIRTUAL_TOUR";
  variants: Record<string, string>;
  sortOrder: number;
};

export type PropertyDetail = {
  id: string;
  referenceCode: string;
  propertyType: PropertyType;
  operation: Operation;
  status: PropertyStatus;
  addressLine: string | null;
  parish: string | null;
  country: string;
  latitude: number | null;
  longitude: number | null;
  price: number;
  currency: string;
  areaM2: number | null;
  rooms: number | null;
  bathrooms: number | null;
  yearBuilt: number | null;
  energyRating: string | null;
  isExclusive: boolean;
  exclusivityEndDate: string | null;
  translations: Translation[];
  media: Media[];
  createdAt: string;
  updatedAt: string;
};

export async function fetchProperty(id: string): Promise<PropertyDetail> {
  const response = await fetch(`${API_BASE_URL}/api/properties/${id}`, { credentials: "include" });
  return response.json();
}

export type UpdatePropertyInput = Partial<CreatePropertyInput> & { status?: PropertyStatus };

export async function updateProperty(id: string, data: UpdatePropertyInput): Promise<PropertyDetail> {
  const response = await fetch(`${API_BASE_URL}/api/properties/${id}`, {
    method: "PATCH",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error("Could not save the listing.");
  }
  return response.json();
}

export async function uploadMedia(
  propertyId: string,
  mediaType: "PHOTO" | "FLOORPLAN",
  files: File[],
): Promise<Media[]> {
  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));
  const response = await fetch(`${API_BASE_URL}/api/properties/${propertyId}/media?mediaType=${mediaType}`, {
    method: "POST",
    credentials: "include",
    // No Content-Type here: the browser sets multipart/form-data with the
    // correct boundary itself when the body is a FormData instance.
    headers: { "X-XSRF-TOKEN": getCsrfToken() ?? "" },
    body: formData,
  });
  if (!response.ok) {
    // Spring's default error body for a ResponseStatusException is JSON
    // with a "message" field holding the reason we passed it
    // (PropertyMediaController's 400s all set one) - surface that verbatim
    // rather than a generic string, falling back if the body isn't JSON.
    let message = "Could not upload the selected files.";
    try {
      const body = await response.json();
      if (typeof body?.message === "string" && body.message) {
        message = body.message;
      }
    } catch {
      // Not JSON - keep the generic message.
    }
    throw new Error(message);
  }
  return response.json();
}

export async function deleteMedia(propertyId: string, mediaId: string): Promise<void> {
  await fetch(`${API_BASE_URL}/api/properties/${propertyId}/media/${mediaId}`, {
    method: "DELETE",
    credentials: "include",
    headers: { "X-XSRF-TOKEN": getCsrfToken() ?? "" },
  });
}

export async function reorderMedia(propertyId: string, mediaIdsInOrder: string[]): Promise<void> {
  await fetch(`${API_BASE_URL}/api/properties/${propertyId}/media/order`, {
    method: "PATCH",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify({ mediaIdsInOrder }),
  });
}

export async function addTranslation(
  propertyId: string,
  locale: string,
  title: string,
  description?: string,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/properties/${propertyId}/translations`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: JSON.stringify({ locale, title, description }),
  });
  if (!response.ok) {
    throw new Error("Could not save the translation.");
  }
}

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
