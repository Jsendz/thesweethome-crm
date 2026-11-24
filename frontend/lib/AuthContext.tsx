"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { fetchMe, type CurrentUser } from "./auth";

type AuthContextValue = {
  user: CurrentUser | null;
  loading: boolean;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const currentUser = await fetchMe();
    setUser(currentUser);
  }, []);

  useEffect(() => {
    (async () => {
      await refresh();
      setLoading(false);
    })();
  }, [refresh]);

  // Spec: "no valid session redirects to /login" on every page except
  // /login itself. Runs once the initial fetchMe() settles, and again
  // whenever `user` later becomes null - e.g. a page that gets a 401 from
  // its own API call (session expired mid-use) calls refresh(), which
  // lands here and sends the user to /login instead of failing silently.
  useEffect(() => {
    if (loading) return;
    if (!user && pathname !== "/login") {
      router.push("/login");
    }
  }, [loading, user, pathname, router]);

  return (
    <AuthContext.Provider value={{ user, loading, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
