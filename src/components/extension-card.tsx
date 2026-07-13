"use client";

import { motion } from "framer-motion";
import { Download, Package } from "lucide-react";
import { type ExtensionMeta, apkUrl, BASE_PATH } from "@/lib/site-config";
import { cn } from "@/lib/utils";

/* Status badge is the ONLY per-card color difference — it's semantic
 * (stable = lime, in progress = coral) so users can tell extension maturity
 * at a glance. Everything else (version pill, release button, debug button,
 * card chrome) is identical across all cards per the design requirement. */
type StatusAccent = "lime" | "coral";

const STATUS: Record<ExtensionMeta["status"], { label: string; accent: StatusAccent }> = {
  stable: { label: "Stable", accent: "lime" },
  beta: { label: "Beta", accent: "lime" },
  wip: { label: "In Progress", accent: "coral" },
};

const STATUS_BADGE: Record<StatusAccent, string> = {
  lime: "bg-accent-lime/10 border-accent-lime/25 text-accent-lime",
  coral: "bg-accent-coral/10 border-accent-coral/25 text-accent-coral",
};
const STATUS_DOT: Record<StatusAccent, string> = {
  lime: "bg-accent-lime",
  coral: "bg-accent-coral",
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
  const status = STATUS[ext.status];
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
        "hover:border-white/[0.14]",
      )}
    >
      {/* ── Row 1: icon + name + site + status ───────────────────────── */}
      <div className="relative flex items-center gap-3.5">
        {/* Icon — no glow, plain rounded icon (consistent across all cards) */}
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

        {/* Status pill — semantic (lime=stable, coral=in progress) */}
        <span
          className={cn(
            "inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1",
            "font-mono text-[10px] font-semibold uppercase tracking-wider",
            STATUS_BADGE[status.accent],
          )}
        >
          <span className={cn("h-1.5 w-1.5 rounded-full", STATUS_DOT[status.accent])} />
          {status.label}
        </span>
      </div>

      {/* ── Row 2: version / build / date pill — SAME (lime) for all cards ── */}
      <div className="relative mt-3.5">
        <span
          className={cn(
            "inline-flex items-center gap-2 rounded-full border px-3 py-1",
            "font-mono text-[11px] font-medium tabular-nums",
            "border-accent-lime/20 bg-accent-lime/10 text-accent-lime",
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

      {/* ── Row 4: download buttons ───────────────────────────────────── */}
      <div className="relative mt-4 flex flex-col gap-2">
        {/* Release button — lime, identical for every card that has one */}
        {hasRelease && (
          <a
            href={apkUrl(ext, "release")}
            download
            className={cn(
              "flex h-11 items-center justify-center gap-2 rounded-xl",
              "font-mono text-sm font-semibold tabular-nums",
              "bg-accent-lime text-bg-base hover:bg-[#d4ff99] shadow-glow-lime",
              "transition-all duration-300 active:scale-[0.98]",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-bg-base focus-visible:ring-current",
            )}
          >
            <Download className="h-4 w-4" />
            Download Release APK
          </a>
        )}

        {/* Debug button — faded-out blue, identical for every card */}
        {hasDebug && (
          <a
            href={apkUrl(ext, "debug")}
            download
            className={cn(
              "flex h-11 items-center justify-center gap-2 rounded-xl",
              "border border-accent-sky/25 bg-accent-sky/10 backdrop-blur",
              "font-mono text-sm font-semibold tabular-nums text-accent-sky/90",
              "transition-all duration-200",
              "hover:bg-accent-sky/20 hover:text-accent-sky hover:border-accent-sky/40",
              "active:scale-[0.98]",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-sky/40",
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
