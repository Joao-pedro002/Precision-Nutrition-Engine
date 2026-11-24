import type { Metadata, Viewport } from 'next';
import localFont from 'next/font/local';
import { PwaRegistrar } from '@/components/PwaRegistrar';
import './globals.css';

const geistSans = localFont({
  src: './fonts/GeistVF.woff',
  variable: '--font-geist-sans',
  weight: '100 900',
});
const geistMono = localFont({
  src: './fonts/GeistMonoVF.woff',
  variable: '--font-geist-mono',
  weight: '100 900',
});

export const metadata: Metadata = {
  title: 'Precision Nutrition | Motor Nutricional',
  description: 'Motor Matemático de Nutrição de Alta Fidelidade (TACO + USDA)',
  applicationName: 'Precision Nutrition',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'black-translucent',
    title: 'Precision Nutrition',
  },
  icons: {
    icon: [
      { url: '/icons/icon-192x192.png', sizes: '192x192', type: 'image/png' },
      { url: '/icons/icon-512x512.png', sizes: '512x512', type: 'image/png' },
    ],
    apple: [
      { url: '/icons/apple-touch-icon.png', sizes: '180x180', type: 'image/png' },
    ],
  },
  formatDetection: {
    telephone: false,
  },
};

export const viewport: Viewport = {
  themeColor: '#020617',
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  colorScheme: 'dark',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" className="dark">
      <head>
        <meta name="color-scheme" content="dark" />
      </head>
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased bg-[#020617] text-slate-100 min-h-screen selection:bg-emerald-500 selection:text-black`}>
        <PwaRegistrar />
        {children}
      </body>
    </html>
  );
}
