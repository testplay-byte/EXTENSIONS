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
import { cn } from "@/lib/utils";

const stableCount = EXTENSIONS.filter((e) => e.status === "stable").length;
const wipCount = EXTENSIONS.filter((e) => e.status === "wip").length;

const heroPills = [
  {
    label: `${EXTENSIONS.length} extensions`,
    icon: Package,
    accent:
      "border-accent-sky/20 bg-accent-sky/5 text-accent-sky",
  },
  {
    label: `${stableCount} stable`,
    icon: ShieldCheck,
    accent:
      "border-accent-lime/20 bg-accent-lime/5 text-accent-lime",
  },
  {
    label: `${wipCount} in progress`,
    icon: Wrench,
    accent:
      "border-accent-coral/20 bg-accent-coral/5 text-accent-coral",
  },
];

export default function Home() {
  return (
    <div className="relative flex min-h-dvh flex-col overflow-x-hidden">
      {/* ── Ambient background orbs (DESIGN §14.3) ────────────────────── */}
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0 -z-10 overflow-hidden"
      >
        <div className="orb-float absolute -left-32 -top-32 h-64 w-64 rounded-full bg-accent-lime/[0.07] blur-[100px]" />
        <div className="orb-float-reverse absolute -right-32 top-1/3 h-64 w-64 rounded-full bg-accent-sky/[0.07] blur-[100px]" />
        <div className="absolute bottom-0 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-accent-coral/[0.04] blur-[120px]" />
      </div>
      {/* Faint dot grid over the whole canvas (DESIGN §14.2) */}
      <div
        aria-hidden
        className="grid-pattern pointer-events-none fixed inset-0 -z-10 opacity-50"
      />

      {/* ── Sticky glass header ──────────────────────────────────────── */}
      <header
        className={cn(
          "sticky top-0 z-40 border-b border-white/[0.06]",
          "bg-bg-base/70 backdrop-blur-xl",
        )}
      >
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between gap-3 px-4 sm:h-16 sm:px-6">
          <a
            href={`${BASE_PATH}/`}
            className="flex min-w-0 items-center gap-2.5"
            aria-label="Aniyomi Extensions — home"
          >
            <img
              src={`${BASE_PATH}/icon.png`}
              alt=""
              width={32}
              height={32}
              className="h-7 w-7 rounded-lg border border-white/[0.08] sm:h-8 sm:w-8"
              style={{
                filter: "drop-shadow(0 0 10px rgba(188, 255, 95, 0.35))",
              }}
            />
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
              "inline-flex h-10 items-center gap-2 rounded-xl border border-white/[0.08]",
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
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-16 pt-12 sm:px-6 sm:pt-16">
        {/* ── Hero ───────────────────────────────────────────────────── */}
        <section className="relative">
          {/* Hero accent radial glow (DESIGN §14) */}
          <div
            aria-hidden
            className="pointer-events-none absolute left-1/2 top-0 -z-10 h-[420px] w-[680px] max-w-[110vw] -translate-x-1/2 -translate-y-1/3 rounded-full blur-[100px]"
            style={{
              background:
                "radial-gradient(closest-side, rgba(188, 255, 95, 0.14), rgba(95, 201, 255, 0.06) 55%, transparent 75%)",
            }}
          />

          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: "easeOut" }}
            className="flex flex-col items-center text-center"
          >
            {/* Eyebrow */}
            <span className="mb-5 inline-flex items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.03] px-3 py-1 font-mono text-[10px] font-semibold uppercase tracking-widest text-text-muted backdrop-blur">
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent-lime opacity-75" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-accent-lime" />
              </span>
              Aniyomi / Animiru compatible
            </span>

            <h1 className="text-balance text-4xl font-extrabold leading-[1.05] tracking-tight text-white sm:text-5xl md:text-6xl">
              Aniyomi{" "}
              <span
                className="bg-gradient-to-r from-accent-lime via-accent-sky to-accent-coral bg-clip-text text-transparent"
                style={{
                  filter: "drop-shadow(0 0 24px rgba(188, 255, 95, 0.25))",
                }}
              >
                Extensions
              </span>
            </h1>

            <p className="mt-4 max-w-2xl text-pretty text-sm leading-relaxed text-text-secondary sm:text-base">
              Anime streaming extensions for Aniyomi / Animiru — built, signed,
              and distributed via GitHub Actions. Pick an extension below and
              tap download to grab the latest APK.
            </p>

            {/* Stat pills */}
            <div className="mt-7 flex flex-wrap items-center justify-center gap-2">
              {heroPills.map((pill) => (
                <span
                  key={pill.label}
                  className={cn(
                    "inline-flex items-center gap-2 rounded-full border px-3.5 py-1.5",
                    "font-mono text-xs font-medium backdrop-blur",
                    pill.accent,
                  )}
                >
                  <pill.icon className="h-3.5 w-3.5" />
                  {pill.label}
                </span>
              ))}
            </div>
          </motion.div>
        </section>

        {/* ── Extension cards grid ───────────────────────────────────── */}
        <section className="mt-12 sm:mt-16">
          <div className="mb-5 flex items-end justify-between gap-4 px-1">
            <h2 className="text-xs font-semibold uppercase tracking-widest text-text-muted">
              Available extensions
            </h2>
            <a
              href={ALL_RELEASES_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="font-mono text-[11px] text-text-dim underline-offset-4 transition-colors hover:text-text-secondary hover:underline"
            >
              all releases →
            </a>
          </div>

          <motion.div
            initial="initial"
            animate="animate"
            variants={{
              animate: { transition: { staggerChildren: 0.07 } },
            }}
            className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3"
          >
            {EXTENSIONS.map((ext, i) => (
              <ExtensionCard key={ext.id} ext={ext} index={i} />
            ))}
          </motion.div>
        </section>

        {/* ── Install note ───────────────────────────────────────────── */}
        <section className="mt-12">
          <div
            className={cn(
              "rounded-2xl border border-white/[0.06] bg-bg-surface/50 p-5 backdrop-blur",
              "sm:p-6",
            )}
          >
            <div className="flex items-start gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-accent-sky/20 bg-accent-sky/10 shadow-glow-step">
                <ShieldCheck className="h-4 w-4 text-accent-sky" />
              </div>
              <div className="min-w-0">
                <h3 className="text-sm font-semibold text-white">
                  Installing an extension APK
                </h3>
                <p className="mt-1.5 text-xs leading-relaxed text-text-muted">
                  Download the APK above, then open it on your device. Allow
                  installs from your browser if prompted. In Aniyomi / Animiru,
                  go to{" "}
                  <span className="font-mono text-text-secondary">
                    Settings → Extensions
                  </span>{" "}
                  and tap the install button if the extension doesn&apos;t appear
                  automatically. Debug builds are unsigned — install them only
                  if the release build fails.
                </p>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* ── Sticky footer (flex-col + mt-auto) ───────────────────────── */}
      <footer
        className={cn(
          "mt-auto shrink-0 border-t border-white/[0.06]",
          "bg-bg-base/60 backdrop-blur-xl",
        )}
      >
        <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-3 px-4 py-5 sm:flex-row sm:px-6">
          <div className="flex items-center gap-2 font-mono text-[11px] text-text-dim">
            <img
              src={`${BASE_PATH}/icon.png`}
              alt=""
              width={16}
              height={16}
              className="h-4 w-4 rounded"
            />
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
