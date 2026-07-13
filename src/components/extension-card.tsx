"use client";

import { motion } from "framer-motion";
import { Download, Package } from "lucide-react";
import { type ExtensionMeta, apkUrl, BASE_PATH } from "@/lib/site-config";
import { cn } from "@/lib/utils";

/* ─────────────────────────────────────────────────────────────────────
 * Per-accent style map. Each entry lists the full Tailwind class strings
 * needed for that accent — Tailwind v4 JIT scans source files for these
 * literal strings, so they must be complete (no concatenation).
 * ───────────────────────────────────────────────────────────────────── */
type AccentName = "lime" | "sky" | "coral";

interface AccentStyle {
  badge: string;
  dot: string;
  versionPill: string;
  primaryBtn: string;
  cardGlow: string;
  hoverBorder: string;
}

const ACCENTS: Record<AccentName, AccentStyle> = {
  lime: {
    badge: "bg-accent-lime/10 border-accent-lime/25 text-accent-lime",
    dot: "bg-accent-lime",
    versionPill: "bg-accent-lime/10 border-accent-lime/20 text-accent-lime",
    primaryBtn:
      "bg-accent-lime text-bg-base hover:bg-[#d4ff99] shadow-glow-lime",
    cardGlow: "bg-accent-lime/10",
    hoverBorder: "group-hover:border-accent-lime/30",
  },
  sky: {
    badge: "bg-accent-sky/10 border-accent-sky/25 text-accent-sky",
    dot: "bg-accent-sky",
    versionPill: "bg-accent-sky/10 border-accent-sky/20 text-accent-sky",
    primaryBtn:
      "bg-accent-sky text-bg-base hover:bg-[#a3dcff] shadow-glow-sky",
    cardGlow: "bg-accent-sky/10",
    hoverBorder: "group-hover:border-accent-sky/30",
  },
  coral: {
    badge: "bg-accent-coral/10 border-accent-coral/25 text-accent-coral",
    dot: "bg-accent-coral",
    versionPill: "bg-accent-coral/10 border-accent-coral/20 text-accent-coral",
    primaryBtn:
      "bg-accent-coral text-white hover:bg-[#ff8aa1] shadow-glow-coral",
    cardGlow: "bg-accent-coral/10",
    hoverBorder: "group-hover:border-accent-coral/30",
  },
};

const STATUS: Record<
  ExtensionMeta["status"],
  { label: string; accent: AccentName }
> = {
  stable: { label: "Stable", accent: "lime" },
  beta: { label: "Beta", accent: "sky" },
  wip: { label: "In Progress", accent: "coral" },
};

const cardVariants = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.35 } },
};

export function ExtensionCard({
  ext,
  index,
}: {
  ext: ExtensionMeta;
  index: number;
}) {
  const cardAccent = ACCENTS[ext.accent];
  const statusAccent = ACCENTS[STATUS[ext.status].accent];
  const hasRelease = ext.availableBuilds.includes("release");
  const hasDebug = ext.availableBuilds.includes("debug");
  const iconSrc = `${BASE_PATH}${ext.icon}`;

  return (
    <motion.article
      variants={cardVariants}
      initial="initial"
      animate="animate"
      transition={{ delay: 0.06 * index }}
      className={cn(
        "group relative overflow-hidden rounded-2xl border border-white/[0.08]",
        "bg-bg-surface/80 backdrop-blur-xl p-5",
        "transition-all duration-300 hover:bg-bg-surface hover:shadow-2xl",
        cardAccent.hoverBorder,
      )}
    >
      {/* In-card decorative glow (top-right) — subtle accent wash */}
      <div
        aria-hidden
        className={cn(
          "pointer-events-none absolute top-0 right-0 h-32 w-32 rounded-full blur-[60px]",
          cardAccent.cardGlow,
        )}
      />

      {/* ── Row 1: icon + name + site + status ───────────────────────── */}
      <div className="relative flex items-center gap-3.5">
        {/* Icon — NO glow around it (per user request); plain rounded icon */}
        <img
          src={iconSrc}
          alt={`${ext.name} icon`}
          width={48}
          height={48}
          loading="lazy"
          className="h-12 w-12 shrink-0 rounded-xl border border-white/[0.08] object-cover"
        />
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-base font-bold tracking-tight text-white">
            {ext.name}
          </h2>
          <p className="mt-0.5 truncate font-mono text-[11px] text-text-muted">
            {ext.site}
          </p>
        </div>

        {/* Status pill (lime for stable, coral for wip) */}
        <span
          className={cn(
            "inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1",
            "font-mono text-[10px] font-semibold uppercase tracking-wider",
            statusAccent.badge,
          )}
        >
          <span className={cn("h-1.5 w-1.5 rounded-full", statusAccent.dot)} />
          {STATUS[ext.status].label}
        </span>
      </div>

      {/* ── Row 2: version / build / date pill (mono, tabular-nums) ──── */}
      <div className="relative mt-3.5">
        <span
          className={cn(
            "inline-flex items-center gap-2 rounded-full border px-3 py-1",
            "font-mono text-[11px] font-medium tabular-nums",
            cardAccent.versionPill,
          )}
        >
          <Package className="h-3 w-3" />
          <span className="font-bold">{ext.version}</span>
          <span className="opacity-50">·</span>
          <span className="text-text-secondary">
            Build{" "}
            <span className="text-white">{String(ext.build).padStart(2, "0")}</span>
          </span>
          <span className="opacity-50">·</span>
          <span className="text-text-muted">{ext.date}</span>
        </span>
      </div>

      {/* ── Row 3: tagline (clamped to 2 lines for consistent card height) */}
      <p className="relative mt-3.5 line-clamp-2 text-[13px] leading-relaxed text-text-secondary">
        {ext.tagline}
      </p>

      {/* ── Row 4: download buttons (same-origin <a download>) ───────── */}
      <div className="relative mt-4 flex flex-col gap-2">
        {hasRelease && (
          <a
            href={apkUrl(ext, "release")}
            download
            className={cn(
              "flex h-11 items-center justify-center gap-2 rounded-xl",
              "font-mono text-sm font-semibold tabular-nums",
              "transition-all duration-300 active:scale-[0.98]",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-bg-base",
              cardAccent.primaryBtn,
              "focus-visible:ring-current",
            )}
          >
            <Download className="h-4 w-4" />
            Download Release APK
          </a>
        )}

        {hasDebug && (
          <a
            href={apkUrl(ext, "debug")}
            download
            className={cn(
              "flex h-11 items-center justify-center gap-2 rounded-xl",
              "border border-white/[0.08] bg-white/[0.03] backdrop-blur",
              "font-mono text-sm font-semibold tabular-nums text-text-secondary",
              "transition-all duration-200",
              "hover:bg-white/[0.06] hover:text-white hover:border-white/[0.12]",
              "active:scale-[0.98]",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20",
              !hasRelease && "h-12",
            )}
          >
            <Download className="h-4 w-4" />
            Download Debug APK
          </a>
        )}
      </div>
    </motion.article>
  );
}
