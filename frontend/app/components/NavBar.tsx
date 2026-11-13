"use client";

import Link from "next/link";
import { useAuth } from "../../lib/AuthContext";
import { logout } from "../../lib/auth";

export function NavBar() {
  const { user, loading, refresh } = useAuth();

  if (loading || !user) {
    return null;
  }

  async function handleLogout() {
    await logout();
    await refresh();
    // Not router.push: NavBar is mounted above every page (including /login
    // itself), and a plain navigation is simpler than threading a router
    // mock through NavBar's own tests, which only mock AuthContext.
    window.location.href = "/login";
  }

  return (
    <nav>
      <span>{user.email}</span>
      {user.role === "ADMIN" && <Link href="/users">Users</Link>}
      <button onClick={handleLogout}>Log out</button>
    </nav>
  );
}
