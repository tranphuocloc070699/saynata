"use client";
/**
 * GSAP setup for the web app. Every animation runs inside `withMotion`, so a visitor who asks for reduced
 * motion gets the final state at once: GSAP's matchMedia only runs the setup when the query matches, and the
 * CSS rule for [data-enter] (globals.css) already shows those elements in that case.
 */
import { gsap } from "gsap";
import { useGSAP } from "@gsap/react";

gsap.registerPlugin(useGSAP);

export const MOTION_OK = "(prefers-reduced-motion: no-preference)";

/** Runs `setup` only when motion is allowed. Call it inside useGSAP so it is cleaned up with the component. */
export function withMotion(setup: () => void): void {
  gsap.matchMedia().add(MOTION_OK, setup);
}

/** Shared timings: short enter, a small rise, one ease (ui-ux-pro-max "Stagger List", standard tier). */
export const ENTER = { duration: 0.42, y: 12, ease: "power2.out", stagger: 0.06 } as const;

export { gsap, useGSAP };
