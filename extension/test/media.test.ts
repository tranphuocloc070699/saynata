// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { isAllowedMedia, isClip, MediaNodes } from "../src/content/media";

const API = "https://api.saynata.com";
const fallback = (src: string) => `chrome-extension://abc/media.html?src=${encodeURIComponent(src)}`;

describe("media addresses", () => {
  it("allows only our server's /media/ files", () => {
    expect(isAllowedMedia("https://api.saynata.com/media/run_1.webp", API)).toBe(true);
    expect(isAllowedMedia("https://evil.example/media/run_1.webp", API)).toBe(false);
    expect(isAllowedMedia("https://api.saynata.com/api/me", API)).toBe(false);
    // The old address is an alias of the new one for now.
    expect(isAllowedMedia("https://api.loffy.me/media/run_1.webp", API)).toBe(true);
    expect(isAllowedMedia("https://evil.example/media/run_1.webp", API)).toBe(false);
    expect(isAllowedMedia("not a url", API)).toBe(false);
  });

  it("knows a clip from a picture", () => {
    expect(isClip("https://api.saynata.com/media/run_1.mp4")).toBe(true);
    expect(isClip("https://api.saynata.com/media/run_1.webp")).toBe(false);
  });
});

describe("MediaNodes", () => {
  it("makes a muted, looping video for a clip and an image for a picture", () => {
    const nodes = new MediaNodes(API, fallback);
    const video = nodes.get("https://api.saynata.com/media/run_1.mp4")?.querySelector("video");
    expect(video).toMatchObject({ muted: true, loop: true, autoplay: true });
    expect(nodes.get("https://api.saynata.com/media/cat_1.webp")?.querySelector("img")?.getAttribute("src")).toBe(
      "https://api.saynata.com/media/cat_1.webp",
    );
  });

  it("gives the same node again, so a re-render does not restart a clip", () => {
    const nodes = new MediaNodes(API, fallback);
    expect(nodes.get("https://api.saynata.com/media/run_1.mp4")).toBe(nodes.get("https://api.saynata.com/media/run_1.mp4"));
  });

  it("gives nothing for another site's address", () => {
    expect(new MediaNodes(API, fallback).get("https://evil.example/media/x.webp")).toBeNull();
  });

  it("shows the extension page when the site blocks the picture", () => {
    const frame = new MediaNodes(API, fallback).get("https://api.saynata.com/media/cat_1.webp")!;
    frame.querySelector("img")!.dispatchEvent(new Event("error"));
    expect(frame.querySelector("iframe")?.getAttribute("src")).toBe(
      "chrome-extension://abc/media.html?src=https%3A%2F%2Fapi.saynata.com%2Fmedia%2Fcat_1.webp",
    );
  });

  it("marks the frame loaded when the picture arrives (it fades in, the size never changes)", () => {
    const frame = new MediaNodes(API, fallback).get("https://api.saynata.com/media/cat_1.webp")!;
    frame.querySelector("img")!.dispatchEvent(new Event("load"));
    expect(frame.classList.contains("loaded")).toBe(true);
  });
});
