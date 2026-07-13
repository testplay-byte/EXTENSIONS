import type { NextConfig } from "next";

// GitHub Pages serves this site at https://testplay-byte.github.io/EXTENSIONS/
// → basePath must be "/EXTENSIONS" so static assets (_next/*) resolve correctly.
// Static export (output: "export") produces a fully static site in ./out/.
const nextConfig: NextConfig = {
  output: "export",
  basePath: "/EXTENSIONS",
  assetPrefix: "/EXTENSIONS/",
  images: {
    unoptimized: true, // required for static export
  },
  typescript: {
    ignoreBuildErrors: true,
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
  reactStrictMode: false,
  trailingSlash: true, // GitHub Pages-friendly URLs
};

export default nextConfig;
