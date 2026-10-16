// Shows one picture or clip inside the card when the web page's CSP blocks it there (src/content/media.ts).
// Only https addresses under /media/; the content script already checked that it is our server.
const src = new URLSearchParams(location.search).get("src") || "";
let url = null;
try {
  url = new URL(src);
} catch {}
if (url && url.protocol === "https:" && url.pathname.startsWith("/media/")) {
  const clip = /\.(mp4|webm|mov)$/i.test(url.pathname);
  const element = document.createElement(clip ? "video" : "img");
  if (clip) {
    element.muted = true;
    element.loop = true;
    element.autoplay = true;
    element.playsInline = true;
  } else {
    element.alt = "";
  }
  element.src = url.href;
  document.body.append(element);
}
