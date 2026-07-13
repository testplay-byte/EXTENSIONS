import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";
import { BASE_PATH } from "@/lib/site-config";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

// Dark Neon is the only theme — themeColor is fixed at the base surface.
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 5,
  themeColor: "#1e1e24",
};

export const metadata: Metadata = {
  title: "Aniyomi Extensions — Download",
  description:
    "Download Aniyomi / Animiru anime streaming extension APKs — AniKoto 180, AnimePahe 180, MKissa 180. Built, signed, and distributed via GitHub Actions.",
  manifest: `${BASE_PATH}/manifest.json`,
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: "Aniyomi Extensions",
  },
  icons: {
    icon: [
      { url: `${BASE_PATH}/pwa-icon-192.png`, sizes: "192x192", type: "image/png" },
      { url: `${BASE_PATH}/pwa-icon-512.png`, sizes: "512x512", type: "image/png" },
    ],
    apple: `${BASE_PATH}/pwa-icon-192.png`,
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark" suppressHydrationWarning>
      <head>
        <link rel="apple-touch-icon" href={`${BASE_PATH}/pwa-icon-192.png`} />
      </head>
      <body
        className={`${geistSans.variable} ${geistMono.variable} font-sans antialiased`}
      >
        {children}
        <Toaster />
      </body>
    </html>
  );
}
