import type { Metadata, Viewport } from 'next';
import localFont from 'next/font/local';
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
  title: 'Precision Nutrition Engine | USFit Pro',
  description: 'Planejamento e rastreamento nutricional de alta precisão com as tabelas TACO 4ª edição e USDA FoodData Central.',
  applicationName: 'Precision Nutrition',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'black-translucent',
    title: 'Precision Nutrition',
  },
  formatDetection: {
    telephone: false,
  },
};

export const viewport: Viewport = {
  themeColor: '#10b981',
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
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
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased bg-[#090d16] text-slate-100 min-h-screen selection:bg-emerald-500 selection:text-black`}>
        {children}
      </body>
    </html>
  );
}
