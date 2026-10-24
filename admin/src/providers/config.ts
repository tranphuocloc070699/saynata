// Backend address. Set VITE_API_URL in admin/.env.local (see .env.example).
export const API_URL: string = (import.meta.env.VITE_API_URL as string | undefined) ?? "https://api.saynata.com";

export const ADMIN_API_URL = `${API_URL}/api/admin`;
