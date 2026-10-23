"use client";
/**
 * The cat that sits on the composer card. Our own pixel drawing (the paid sprite pack is not used on the web,
 * see TASK-102). Asleep while the box is empty, awake while the learner types, a small hop while Saynata works.
 * All movement is GSAP and stops for reduced motion.
 */
import { useRef } from "react";
import { gsap, useGSAP, withMotion } from "@/lib/motion";

export type CatMood = "asleep" | "awake" | "busy";

// A loaf cat, head on the right. # outline, o fur, l closed eye, e open eye, b blush and inner ear, n mouth.
// Made from filled shapes with the outline drawn around each, so the head stands apart from the body.
const ASLEEP = [
  "................#........#..",
  "...............#o#......#o#.",
  "...............#oo######oo#.",
  "...............#oboooooobo#.",
  "..............#oooooooooooo#",
  "....###########oooooooooooo#",
  "...#oooooooooo#ooolloollooo#",
  "..#ooooooooooo#oooooooooooo#",
  ".#oooooooooooo#ooobonnobooo#",
  "#o#ooooooooooo#oooooooooooo#",
  "#o#oooooooooooo#oooooooooo#.",
  ".#oooooooooooooo##########..",
  "..##oooooooooooooooo#.......",
  "....################........",
];
const AWAKE = [
  "................#........#..",
  "...............#o#......#o#.",
  "...............#oo######oo#.",
  "...............#oboooooobo#.",
  "..............#oooooooooooo#",
  "....###########oooeooooeooo#",
  "...#oooooooooo#oooeooooeooo#",
  "..#ooooooooooo#oooooooooooo#",
  ".#oooooooooooo#ooobonnobooo#",
  "#o#ooooooooooo#oooooooooooo#",
  "#o#oooooooooooo#oooooooooo#.",
  ".#oooooooooooooo##########..",
  "..##oooooooooooooooo#.......",
  "....################........",
];

const FILL: Record<string, string> = {
  "#": "var(--cat-line)",
  l: "var(--cat-line)",
  e: "var(--cat-line)",
  o: "var(--cat)",
  b: "var(--cat-blush)",
  n: "var(--cat-line)",
};

function Pixels({ map }: { map: string[] }) {
  return map.flatMap((row, y) =>
    [...row].map((c, x) =>
      FILL[c] ? <rect key={`${x}-${y}`} x={x} y={y} width={1.02} height={1.02} fill={FILL[c]} /> : null,
    ),
  );
}

export function PixelCat({ mood, className = "" }: { mood: CatMood; className?: string }) {
  const root = useRef<SVGSVGElement>(null);
  const width = Math.max(...ASLEEP.map((r) => r.length));

  useGSAP(
    () => {
      withMotion(() => {
        // Breathing: the body rises a little from its bottom edge.
        gsap.to(".cat-body", {
          scaleY: mood === "asleep" ? 1.045 : 1.02,
          transformOrigin: "50% 100%",
          duration: mood === "asleep" ? 1.6 : 0.9,
          ease: "sine.inOut",
          yoyo: true,
          repeat: -1,
        });
        if (mood === "asleep") {
          // Three z letters drift up from the head, one after another.
          gsap.fromTo(
            ".cat-z",
            { autoAlpha: 0, x: 0, y: 0, scale: 0.6 },
            {
              autoAlpha: 1,
              x: 3,
              y: -6,
              scale: 1,
              duration: 1.8,
              ease: "sine.out",
              stagger: { each: 0.6, repeat: -1 },
            },
          );
        }
        if (mood === "busy") {
          gsap.to(".cat-body", { y: -1.4, duration: 0.22, ease: "power1.out", yoyo: true, repeat: -1, repeatDelay: 0.25 });
        }
      });
    },
    { scope: root, dependencies: [mood], revertOnUpdate: true },
  );

  return (
    <svg
      ref={root}
      viewBox={`0 -8 ${width + 6} ${ASLEEP.length + 8}`}
      shapeRendering="crispEdges"
      className={className}
      role="img"
      aria-label={mood === "asleep" ? "A sleeping cat" : "A cat watching you type"}
    >
      <g className="cat-body">
        <Pixels map={mood === "asleep" ? ASLEEP : AWAKE} />
      </g>
      {mood === "asleep" && (
        <g fill="var(--muted)" fontFamily="var(--font-dm-serif), serif" fontSize={4} aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <text key={i} className="cat-z" x={25 + i * 1.5} y={-1 - i * 2.5} opacity={0}>
              z
            </text>
          ))}
        </g>
      )}
    </svg>
  );
}
