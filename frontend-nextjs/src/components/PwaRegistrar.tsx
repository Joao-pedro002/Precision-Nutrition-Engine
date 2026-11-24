'use client';

import { useEffect } from 'react';

export function PwaRegistrar() {
  useEffect(() => {
    if (
      typeof window === 'undefined' ||
      !('serviceWorker' in navigator) ||
      !window.isSecureContext
    ) {
      return;
    }

    navigator.serviceWorker
      .register('/sw.js', { scope: '/' })
      .catch(() => {
        // Falha silenciosa em ambientes locais sem HTTPS ou com bloqueio de SW
      });
  }, []);

  return null;
}
