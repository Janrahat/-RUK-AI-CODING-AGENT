import type { Metadata, Viewport } from "next";
import "./globals.css";
export const metadata: Metadata = { title: "RUK AI GLOBAL", description: "Your personal AI assistant", applicationName: "RUK AI GLOBAL", manifest: "/manifest.webmanifest" };
export const viewport: Viewport = { themeColor: "#080b16" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
