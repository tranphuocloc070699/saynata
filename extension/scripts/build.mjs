// Builds the unpacked extension into dist/. Each entry is its own bundle with no shared chunks,
// because a content script cannot import modules: background.js (ES module), content.js (IIFE),
// and popup/ when it exists. Then writes manifest.json.
//
//   node scripts/build.mjs            # one build
//   node scripts/build.mjs --watch    # rebuild on change (reload the extension in the browser)
//   VITE_API_BASE=http://localhost:8081 node scripts/build.mjs   # a local backend
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "vite";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const outDir = resolve(root, "dist");
const watch = process.argv.includes("--watch") ? {} : null;
const apiBase = process.env.VITE_API_BASE ?? "https://api.saynata.com";
// The old address stays allowed for one release, so an updated extension still works if the server is reached by either (TASK-104).
const legacyBase = "https://api.loffy.me";
const pkg = JSON.parse(readFileSync(resolve(root, "package.json"), "utf8"));

rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });

const shared = { root, configFile: false, logLevel: "warn", define: { "import.meta.env.VITE_API_BASE": JSON.stringify(apiBase) } };

async function script(entry, fileName, format) {
  await build({
    ...shared,
    publicDir: false,
    build: {
      outDir,
      emptyOutDir: false,
      watch,
      minify: false,
      lib: { entry: resolve(root, entry), formats: [format], name: "saynata", fileName: () => fileName },
    },
  });
}

writeFileSync(resolve(outDir, "manifest.json"), JSON.stringify(manifest(), null, 2));
cpSync(resolve(root, "public"), outDir, { recursive: true });
await script("src/background/index.ts", "background.js", "es");
await script("src/content/index.ts", "content.js", "iife");
if (existsSync(resolve(root, "src/popup/index.html"))) {
  await build({
    ...shared,
    root: resolve(root, "src/popup"),
    publicDir: false,
    base: "./",
    build: { outDir: resolve(outDir, "popup"), emptyOutDir: false, watch, minify: false },
  });
}
console.log(`Built ${pkg.name} ${pkg.version} into dist/ (API ${apiBase})`);

function manifest() {
  const origin = new URL(apiBase).origin;
  return {
    manifest_version: 3,
    name: "Saynata",
    short_name: "Saynata",
    version: pkg.version,
    description:
      "Select English on any page to see a simple English explanation. Selected text is sent to api.saynata.com and Claude.",
    icons: { 16: "icons/icon-16.png", 32: "icons/icon-32.png", 48: "icons/icon-48.png", 128: "icons/icon-128.png" },
    action: {
      default_title: "Saynata",
      default_icon: { 16: "icons/icon-16.png", 32: "icons/icon-32.png" },
      ...(existsSync(resolve(root, "src/popup/index.html")) ? { default_popup: "popup/index.html" } : {}),
    },
    background: { service_worker: "background.js", type: "module" },
    content_scripts: [{ matches: ["<all_urls>"], js: ["content.js"], run_at: "document_idle" }],
    // The card shows a picture in this page when the site's CSP blocks our server (src/content/media.ts).
    web_accessible_resources: [{ resources: ["media.html", "media.js"], matches: ["<all_urls>"] }],
    permissions: ["contextMenus", "storage", "activeTab", "scripting"],
    host_permissions: [...new Set([origin, new URL(legacyBase).origin])].map((o) => `${o}/*`),
    commands: {
      "explain-selection": {
        suggested_key: { default: "Alt+Shift+S" },
        description: "Explain the selected text",
      },
    },
  };
}
