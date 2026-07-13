/**
 * Site configuration — shared constants for the download webpage.
 * Served at https://testplay-byte.github.io/EXTENSIONS/ (GitHub Pages project site).
 */

// The GitHub repo (owner/name) — used for release/download URLs.
export const GITHUB_REPO = "testplay-byte/EXTENSIONS";

// Base path matches next.config.ts `basePath`. Prepend to raw asset URLs
// (e.g. <img src={`${BASE_PATH}/icon.png`} />). next/image & next/link handle
// basePath automatically — this is only for raw <img>/<a> tags.
export const BASE_PATH = "/EXTENSIONS";

// The GitHub Releases base URL. APKs are attached to releases as assets.
// "latest/download/<filename>" always serves the most recent release's asset.
export const RELEASES_BASE = `https://github.com/${GITHUB_REPO}/releases/latest/download`;
export const ALL_RELEASES_URL = `https://github.com/${GITHUB_REPO}/releases`;
export const REPO_URL = `https://github.com/${GITHUB_REPO}`;

// Per-extension metadata. `version` MUST match the versionName in
// EXTENSIONS/<id>/DEV/src/en/<id>/build.gradle.kts (versionName = "16." + extVersionCode).
// The apk filename comes from base.archivesName.set("aniyomi-en.<id>180-v$versionName").
export type BuildType = "release" | "debug";
export type ExtStatus = "stable" | "beta" | "wip";

export interface ExtensionMeta {
  id: string;
  name: string;
  tagline: string;
  version: string; // e.g. "v16.9"
  build: number;
  date: string;
  status: ExtStatus;
  availableBuilds: BuildType[];
  icon: string; // path under /public, served at `${BASE_PATH}/${icon}`
  letter: string; // 2-letter fallback for avatar
  site: string; // target site domain
  accent: "lime" | "sky" | "coral"; // DESIGN.md neon accent for this card
  features: string[]; // short feature bullets
}

export const EXTENSIONS: ExtensionMeta[] = [
  {
    id: "anikoto",
    name: "AniKoto 180",
    tagline:
      "Anime streaming extension for anikototv.to — 4 video servers + Kiwi-Stream, smart search, episode metadata, fork compatibility.",
    version: "v16.9",
    build: 9,
    date: "June 27, 2027",
    status: "stable",
    availableBuilds: ["release", "debug"],
    icon: "/icon.png",
    letter: "AK",
    site: "anikototv.to",
    accent: "lime",
    features: ["4 video servers", "Smart search", "Episode metadata", "R8 release, signed"],
  },
  {
    id: "animepahe",
    name: "AnimePahe 180",
    tagline:
      "Anime streaming extension for animepahe.pw — popular, latest, search, filters, details, episodes + Kwik HLS video playback.",
    version: "v16.10",
    build: 10,
    date: "June 28, 2027",
    status: "stable",
    availableBuilds: ["release", "debug"],
    icon: "/animepahe-icon.png",
    letter: "AP",
    site: "animepahe.pw",
    accent: "sky",
    features: ["Popular + latest", "Search + filters", "Kwik HLS playback", "Metadata enrichment"],
  },
  {
    id: "mkissa",
    name: "MKissa 180",
    tagline:
      "Anime streaming extension for mkissa.to — catalog, details, episodes, metadata enrichment + 6 video servers (3 working).",
    version: "v16.17",
    build: 17,
    date: "June 29, 2027",
    status: "wip",
    availableBuilds: ["debug"],
    icon: "/mkissa-icon.png",
    letter: "MK",
    site: "mkissa.to",
    accent: "coral",
    features: ["Catalog + details", "6 video servers", "Server toggle settings", "In progress"],
  },
];

// Build the GitHub Release download URL for an extension's APK.
// Filename pattern: aniyomi-en.<id>180-v<version>-<type>.apk
export function apkUrl(ext: ExtensionMeta, type: BuildType): string {
  // version is like "v16.9" → keep as-is (matches base.archivesName "v$versionName")
  const filename = `aniyomi-en.${ext.id}180-${ext.version}-${type}.apk`;
  return `${RELEASES_BASE}/${filename}`;
}
