import type { AuthProvider } from "@refinedev/core";
import type { AdminUser } from "@/api/types";
import { API_URL } from "./config";

const AUTH_URL = `${API_URL}/api/admin/auth`;

// The login cookie is HttpOnly, so JavaScript never sees it: we only send it back (credentials: 'include').
async function authFetch(path: string, init: RequestInit = {}) {
  return fetch(`${AUTH_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: { "Content-Type": "application/json", ...init.headers },
  });
}

async function fetchMe(): Promise<AdminUser | null> {
  const response = await authFetch("/me");
  return response.ok ? ((await response.json()) as AdminUser) : null;
}

export const authProvider: AuthProvider = {
  login: async ({ email, password }) => {
    const response = await authFetch("/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (response.ok) {
      return { success: true, redirectTo: "/words" };
    }
    const message = response.status === 429 ? "Too many tries. Wait a minute and try again." : "Wrong email or password.";
    return { success: false, error: { name: "Login failed", message } };
  },

  logout: async () => {
    await authFetch("/logout", { method: "POST" });
    return { success: true, redirectTo: "/login" };
  },

  check: async () => {
    const user = await fetchMe();
    if (user && user.roles.includes("ADMIN")) {
      return { authenticated: true };
    }
    return { authenticated: false, redirectTo: "/login" };
  },

  getIdentity: async () => {
    const user = await fetchMe();
    return user ? { id: user.id, name: user.displayName, email: user.email } : undefined;
  },

  // A 401 from any call means the session ended: go back to login.
  onError: async (error) => {
    if (error?.statusCode === 401) {
      return { logout: true, redirectTo: "/login" };
    }
    return { error };
  },
};
