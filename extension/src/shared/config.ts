/** The backend address. `VITE_API_BASE=http://localhost:8081 npm run build` points a build at a local backend. */
export const API_BASE: string = (import.meta.env.VITE_API_BASE as string | undefined) ?? "https://api.saynata.com";

/** The old address (TASK-104). It still works as an alias, so media links the server still writes with it are trusted too. */
export const LEGACY_API_BASE = "https://api.loffy.me";
