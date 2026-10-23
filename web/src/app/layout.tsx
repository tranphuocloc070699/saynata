import type { Metadata, Viewport } from "next";
import { DM_Serif_Display, Newsreader } from "next/font/google";
import "./globals.css";

// DM Serif Display is the logo's "S"; Newsreader stands in for New York, the iOS meaning font.
const dmSerif = DM_Serif_Display({ variable: "--font-dm-serif", subsets: ["latin"], weight: "400" });
const newsreader = Newsreader({ variable: "--font-newsreader", subsets: ["latin", "vietnamese"] });

export const metadata: Metadata = {
  title: "Saynata",
  description: "Type an English word or sentence. Saynata explains it in simple English first, your language last.",
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f3f5f1" },
    { media: "(prefers-color-scheme: dark)", color: "#121715" },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${dmSerif.variable} ${newsreader.variable} h-full antialiased`}>
      <head>
        <noscript>
          <style>{"[data-enter]{opacity:1!important}"}</style>
        </noscript>
      </head>
      <body className="flex min-h-full flex-col font-sans">{children}</body>
    </html>
  );
}
