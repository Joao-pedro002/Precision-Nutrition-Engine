import React from 'react';

export default function OfflinePage() {
  return (
    <main
      style={{
        minHeight: '100vh',
        backgroundColor: '#020617',
        color: '#f1f5f9',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px 16px',
        fontFamily:
          'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        boxSizing: 'border-box',
      }}
    >
      <style>{`
        html, body {
          margin: 0;
          padding: 0;
          background-color: #020617;
          color: #f1f5f9;
        }
        .offline-retry-btn {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          width: 100%;
          box-sizing: border-box;
          padding: 13px 20px;
          border-radius: 16px;
          background-color: #10b981;
          color: #020617;
          font-weight: 700;
          font-size: 14px;
          text-decoration: none;
          box-shadow: 0 10px 25px -5px rgba(16, 185, 129, 0.28);
          transition: background-color 150ms ease, transform 150ms ease;
        }
        .offline-retry-btn:hover {
          background-color: #34d399;
        }
        .offline-retry-btn:active {
          transform: scale(0.98);
        }
      `}</style>

      <div
        style={{
          width: '100%',
          maxWidth: '420px',
          backgroundColor: 'rgba(15, 23, 42, 0.85)',
          border: '1px solid #1e293b',
          borderRadius: '24px',
          padding: '32px',
          textAlign: 'center',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.65)',
          position: 'relative',
          overflow: 'hidden',
          boxSizing: 'border-box',
        }}
      >
        {/* Subtle emerald ambient glow */}
        <div
          aria-hidden="true"
          style={{
            position: 'absolute',
            top: '-72px',
            right: '-72px',
            width: '200px',
            height: '200px',
            borderRadius: '9999px',
            background:
              'radial-gradient(circle, rgba(16,185,129,0.18) 0%, rgba(16,185,129,0) 70%)',
            pointerEvents: 'none',
          }}
        />

        {/* Inline SVG Icon (zero JS/external dependency) */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            marginBottom: '20px',
          }}
        >
          <div
            style={{
              width: '64px',
              height: '64px',
              borderRadius: '18px',
              backgroundColor: '#020617',
              border: '1px solid #1e293b',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <svg
              width="28"
              height="28"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#34d399"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <line x1="2" x2="22" y1="2" y2="22" />
              <path d="M8.5 16.5a5 5 0 0 1 7 0" />
              <path d="M2 8.82a15 15 0 0 1 4.17-2.65" />
              <path d="M10.66 5c4.01-.36 8.14.9 11.34 3.76" />
              <path d="M16.85 11.25a10 10 0 0 1 2.22 1.68" />
              <path d="M5 12.859a10 10 0 0 1 5.17-2.69" />
              <line x1="12" x2="12.01" y1="20" y2="20" />
            </svg>
          </div>
        </div>

        {/* Brand Badge */}
        <div style={{ marginBottom: '12px' }}>
          <span
            style={{
              display: 'inline-block',
              fontSize: '11px',
              fontWeight: 700,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              padding: '4px 12px',
              borderRadius: '9999px',
              backgroundColor: 'rgba(16, 185, 129, 0.12)',
              color: '#34d399',
              border: '1px solid rgba(16, 185, 129, 0.25)',
            }}
          >
            Precision Nutrition
          </span>
        </div>

        <h1
          style={{
            margin: '0 0 10px 0',
            fontSize: '22px',
            fontWeight: 900,
            letterSpacing: '-0.02em',
            color: '#ffffff',
          }}
        >
          Você está sem conexão
        </h1>

        <p
          style={{
            margin: '0 0 24px 0',
            fontSize: '14px',
            lineHeight: 1.6,
            color: '#94a3b8',
          }}
        >
          Não foi possível alcançar o servidor do Precision Nutrition neste
          momento. Verifique sua internet e tente novamente.
        </p>

        {/* Native HTML anchor link (works without JS hydration) */}
        <a href="/" className="offline-retry-btn">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
            <path d="M21 3v5h-5" />
            <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
            <path d="M8 16H3v5" />
          </svg>
          <span>Tentar Novamente</span>
        </a>
      </div>
    </main>
  );
}
