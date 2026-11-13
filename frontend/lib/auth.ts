import { API_BASE_URL } from "./apiBaseUrl";

export type CurrentUser = { id: string; email: string; fullName: string; role: "ADMIN" | "MANAGER" | "AGENT" };

export function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? match[1] : null;
}

export async function fetchMe(): Promise<CurrentUser | null> {
  const response = await fetch(`${API_BASE_URL}/api/auth/me`, { credentials: "include" });
  if (!response.ok) return null;
  return response.json();
}

export async function login(email: string, password: string): Promise<boolean> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "X-XSRF-TOKEN": getCsrfToken() ?? "",
    },
    body: new URLSearchParams({ email, password }).toString(),
  });
  return response.ok;
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
    headers: { "X-XSRF-TOKEN": getCsrfToken() ?? "" },
  });
}
