import { Suspense } from "react";
import Image from "next/image";
import { Home } from "@/components/home/Home";

/**
 * The homepage (TASK-102.1, direction B): one reading column, the composer on top, the result below.
 * The header and the intro are static HTML; the tool reads ?q= on the client, inside Suspense.
 */
export default function Page() {
  return (
    <>
      <header className="mx-auto flex w-full max-w-[680px] items-center gap-2.5 px-4 pt-5 sm:px-6">
        <Image src="/logo.svg" alt="" width={32} height={32} className="rounded-[9px]" priority />
        <span className="font-display text-[1.35rem] leading-none">Saynata</span>
      </header>
      <main className="mx-auto flex w-full max-w-[680px] flex-1 flex-col gap-6 px-4 pt-10 pb-16 sm:px-6 sm:pt-14">
        <div data-enter className="flex flex-col items-center gap-2 text-center">
          <h1 className="font-display text-[2.1rem] leading-tight text-balance sm:text-[2.6rem]">
            Learn English in English.
          </h1>
          <p className="max-w-[46ch] font-serif text-[1.05rem] text-ink-2">
            Type a word or a sentence. Saynata explains it in simple English first, and your language waits at the
            bottom.
          </p>
        </div>
        <Suspense fallback={<div className="min-h-[170px]" />}>
          <Home />
        </Suspense>
      </main>
      <footer className="mx-auto w-full max-w-[680px] px-4 pb-8 text-center text-xs text-muted sm:px-6">
        Simple English first. Your language last.
      </footer>
    </>
  );
}
