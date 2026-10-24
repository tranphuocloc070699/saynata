import type { NextConfig } from "next";

/**
 * The backend the /api rewrite points at. In production Caddy sends /api to the backend on the same domain,
 * so this rewrite is only for `next dev` and `next start` (doc-23). The backend has no CORS setup.
 */
const apiOrigin = process.env.SAYNATA_API_ORIGIN ?? "https://api.loffy.me";

const nextConfig: NextConfig = {
  // A small self-contained server (.next/standalone) that runs in the web container (doc-23, Deploy).
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiOrigin}/api/:path*` }];
  },
};

export default nextConfig;
