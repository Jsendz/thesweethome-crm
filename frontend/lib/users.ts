import { API_BASE_URL } from "./apiBaseUrl";

// Mirrors the backend's UserDirectoryEntry (see
// backend/.../user/UserDirectoryEntry.java) - deliberately just id/fullName,
// unlike the ADMIN-only GET /api/users this does not reuse.
export type UserDirectoryEntry = {
  id: string;
  fullName: string;
};

export async function fetchUserDirectory(): Promise<UserDirectoryEntry[]> {
  const response = await fetch(`${API_BASE_URL}/api/users/directory`, { credentials: "include" });
  return response.json();
}
