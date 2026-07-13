"use client";

import { motion } from "framer-motion";
import { Github, Package, ShieldCheck, Wrench } from "lucide-react";
import {
  EXTENSIONS,
  BASE_PATH,
  REPO_URL,
  ALL_RELEASES_URL,
} from "@/lib/site-config";
import { ExtensionCard } from "@/components/extension-card";
import { Logo } from "@/components/logo";
import { cn } from "@/lib/utils";

const stableCount = EXTENSIONS.filter((e) => e.status === "stable").length;
const wipCount = EXTENSIONS.filter((e) => e.status === "wip").length;

const heroPills = [
  {
    label: `${EXTENSIONS.length} extensions`,
    icon: Package,
    accent: "border-accent-sky/20 bg-accent-sky/5 text-accent-sky",
  },
  {
    label: `${stableCount} stable`,
    icon: ShieldCheck,
    accent: "border-accent-lime/20 bg-accent-lime/5 text-accent-lime",
  },
  {
    label: `${wipCount} in progress`,
    icon: Wrench,
    accent: "border-accent-coral/20 bg-accent-coral/5 text-accent-coral",
  },
];

export default function Home() {
  return (
    <div className="relative flex min-h-dvh flex-col overflow-x-hidden">
      {/* ── Structured background: line grid + dot grid (restored) ─────── */}
      <div aria-hidden className="pointer-events-none fixed inset-0 -z-10">
        {/* Faint line grid — the primary structured pattern */}
        <div className="line-grid absolute inset-0 opacity-100" />
        {/* Dot grid overlaid at the same spacing — dots sit at the line
            intersections, giving a clean technical grid feel without color. */}
        <div className="dot-grid absolute inset-0 opacity-100" />
        {/* Soft top-down fade so the grid is subtler at the very top/bottom
            and the content stays the focal point. */}
        <div
          className="absolute inset-0"
          style={{
            background:
              "linear-gradient(to bottom, rgba(30,30,36,0.4) 0%, transparent 18%, transparent 82%, rgba(30,30,36,0.4) 100%)",
          }}
        />
      </div>

      {/* ── Floating glass nav bar (NOT sticky — scrolls with the page) ── */}
      <header className="relative z-40 px-4 pt-4 sm:pt-6">
        <div
          className={cn(
            "mx-auto flex h-14 w-full max-w-5xl items-center justify-between gap-3 rounded-2xl",
            "border border-white/[0.08] bg-bg-surface/60 backdrop-blur-xl",
            "px-4 shadow-[0_8px_32px_rgba(0,0,0,0.45)] sm:px-5",
          )}
        >
          <a
            href={`${BASE_PATH}/`}
            className="flex min-w-0 items-center gap-2.5"
            aria-label="Aniyomi Extensions — home"
          >
            <Logo className="h-7 w-7 sm:h-8 sm:w-8" />
            <span className="truncate text-sm font-bold tracking-tight text-white sm:text-base">
              Aniyomi <span className="text-accent-lime">Extensions</span>
            </span>
          </a>

          <a
            href={REPO_URL}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="View on GitHub"
            className={cn(
              "inline-flex h-9 items-center gap-2 rounded-xl border border-white/[0.08]",
              "bg-white/[0.03] px-3 font-mono text-xs text-text-secondary",
              "transition-all duration-200",
              "hover:border-white/[0.12] hover:bg-white/[0.06] hover:text-white",
              "active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-lime/30",
            )}
          >
            <Github className="h-4 w-4" />
            <span className="hidden sm:inline">GitHub</span>
          </a>
        </div>
      </header>

      {/* ── Main ─────────────────────────────────────────────────────── */}
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 pb-16 sm:px-6">
        {/* ── Hero (minimal: one headline + stats in a single row) ────── */}
        <section className="flex flex-col items-center pt-10 sm:pt-14">
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: "easeOut" }}
            className="flex flex-col items-center text-center"
          >
            <h1 className="text-2xl font-extrabold tracking-tight text-white sm:text-3xl">
              Aniyomi{" "}
              <span className="bg-gradient-to-r from-accent-lime via-accent-sky to-accent-coral bg-clip-text text-transparent">
                Extensions
              </span>
            </h1>

            {/* Stat pills — forced single row */}
            <div className="mt-4 flex flex-nowrap items-center justify-center gap-2">
              {heroPills.map((pill) => (
                <span
                  key={pill.label}
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1",
                    "font-mono text-[11px] font-medium backdrop-blur whitespace-nowrap",
                    pill.accent,
                  )}
                >
                  <pill.icon className="h-3 w-3" />
                  {pill.label}
                </span>
              ))}
            </div>
          </motion.div>
        </section>

        {/* ── Extension cards grid ───────────────────────────────────── */}
        <section className="mt-10 sm:mt-12">
          <motion.div
            initial="initial"
            animate="animate"
            variants={{
              animate: { transition: { staggerChildren: 0.07 } },
            }}
            className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
          >
            {EXTENSIONS.map((ext, i) => (
              <ExtensionCard key={ext.id} ext={ext} index={i} />
            ))}
          </motion.div>
        </section>
      </main>

      {/* ── Footer (sticky to bottom via flex-col + mt-auto) ──────────── */}
      <footer
        className={cn(
          "mt-auto shrink-0 border-t border-white/[0.06]",
          "bg-bg-base/60 backdrop-blur-xl",
        )}
      >
        <div className="mx-auto flex w-full max-w-5xl flex-col items-center justify-between gap-3 px-4 py-5 sm:flex-row sm:px-6">
          <div className="flex items-center gap-2 font-mono text-[11px] text-text-dim">
            <Logo className="h-4 w-4" />
            Built by{" "}
            <span className="text-text-muted">Confused_creature_180</span>
          </div>

          <div className="flex items-center gap-4 font-mono text-[11px] text-text-dim">
            <a
              href={REPO_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="transition-colors hover:text-text-secondary"
            >
              repository
            </a>
            <span className="opacity-30">·</span>
            <a
              href={ALL_RELEASES_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="transition-colors hover:text-text-secondary"
            >
              all releases
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
