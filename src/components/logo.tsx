import { cn } from "@/lib/utils";

/**
 * Vector brand mark for Aniyomi Extensions.
 * A rounded square (the extension "package") with a play triangle (anime/media),
 * in the lime accent. Replaces the old raster /icon.png which wasn't the right
 * brand icon. Used in the nav bar and footer.
 */
export function Logo({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn("h-8 w-8", className)}
      role="img"
      aria-label="Aniyomi Extensions"
    >
      <defs>
        <linearGradient
          id="logo-grad"
          x1="2"
          y1="2"
          x2="30"
          y2="30"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#bcff5f" />
          <stop offset="1" stopColor="#7fd83a" />
        </linearGradient>
      </defs>
      <rect x="2" y="2" width="28" height="28" rx="8" fill="url(#logo-grad)" />
      <rect
        x="2.5"
        y="2.5"
        width="27"
        height="27"
        rx="7.5"
        stroke="white"
        strokeOpacity="0.25"
      />
      <path
        d="M13 10.2c0-.9 1-1.45 1.78-.96l7.2 4.8c.72.48.72 1.54 0 2.02l-7.2 4.8c-.78.52-1.78-.03-1.78-.96V10.2z"
        fill="#161a12"
      />
    </svg>
  );
}
