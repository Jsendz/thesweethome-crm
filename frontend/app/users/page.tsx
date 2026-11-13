"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "../../lib/AuthContext";
import { getCsrfToken } from "../../lib/auth";
import { API_BASE_URL } from "../../lib/apiBaseUrl";

type Role = "ADMIN" | "MANAGER" | "AGENT";

type User = {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
};

export default function UsersPage() {
  const router = useRouter();
  const { user, loading } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState<Role>("AGENT");
  const [error, setError] = useState<string | null>(null);

  const isAdmin = !loading && user && user.role === "ADMIN";

  async function loadUsers() {
    const response = await fetch(`${API_BASE_URL}/api/users`, { credentials: "include" });
    if (response.ok) {
      setUsers(await response.json());
    }
  }

  useEffect(() => {
    if (loading) return;
    if (!user || user.role !== "ADMIN") {
      router.push("/");
      return;
    }
    loadUsers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, user]);

  if (loading || !isAdmin) {
    return null;
  }

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    const response = await fetch(`${API_BASE_URL}/api/users`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCsrfToken() ?? "",
      },
      body: JSON.stringify({ email, password, fullName, role }),
    });
    if (response.ok) {
      setEmail("");
      setPassword("");
      setFullName("");
      setRole("AGENT");
      await loadUsers();
    } else {
      setError("Could not create user.");
    }
  }

  async function handleToggleActive(targetUser: User) {
    await fetch(`${API_BASE_URL}/api/users/${targetUser.id}`, {
      method: "PATCH",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCsrfToken() ?? "",
      },
      body: JSON.stringify({ active: !targetUser.active }),
    });
    await loadUsers();
  }

  return (
    <main>
      <h1>Users</h1>

      <table>
        <thead>
          <tr>
            <th>Email</th>
            <th>Full name</th>
            <th>Role</th>
            <th>Active</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.email}</td>
              <td>{u.fullName}</td>
              <td>{u.role}</td>
              <td>{u.active ? "Yes" : "No"}</td>
              <td>
                <button onClick={() => handleToggleActive(u)}>
                  {u.active ? "Deactivate" : "Reactivate"}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Create user</h2>
      <form onSubmit={handleCreate}>
        <label htmlFor="new-email">Email</label>
        <input
          id="new-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        <label htmlFor="new-password">Password</label>
        <input
          id="new-password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        <label htmlFor="new-full-name">Full name</label>
        <input
          id="new-full-name"
          type="text"
          value={fullName}
          onChange={(event) => setFullName(event.target.value)}
        />

        <label htmlFor="new-role">Role</label>
        <select
          id="new-role"
          value={role}
          onChange={(event) => setRole(event.target.value as Role)}
        >
          <option value="ADMIN">ADMIN</option>
          <option value="MANAGER">MANAGER</option>
          <option value="AGENT">AGENT</option>
        </select>

        <button type="submit">Create user</button>

        {error && <p>{error}</p>}
      </form>
    </main>
  );
}
