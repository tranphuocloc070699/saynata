/** The backend address. `VITE_API_BASE=http://localhost:8081 npm run build` points a build at a local backend. */
export const API_BASE: string = (import.meta.env.VITE_API_BASE as string | undefined) ?? "https://api.loffy.me";
