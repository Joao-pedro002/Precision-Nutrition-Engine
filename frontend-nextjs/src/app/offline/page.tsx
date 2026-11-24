'use client';

import React from 'react';
import { WifiOff, RefreshCw, Sparkles } from 'lucide-react';

export default function OfflinePage() {
  const handleRetry = () => {
    if (typeof window !== 'undefined') {
      window.location.reload();
    }
  };

  return (
    <main className="min-h-screen bg-[#020617] text-slate-100 flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md bg-slate-900/70 border border-slate-800 rounded-3xl p-8 shadow-2xl text-center space-y-6 relative overflow-hidden">
        <div className="absolute -top-20 -right-20 w-56 h-56 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="flex justify-center">
          <div className="w-16 h-16 rounded-2xl bg-slate-950 border border-slate-800 flex items-center justify-center shadow-inner relative">
            <WifiOff className="w-7 h-7 text-emerald-400" />
          </div>
        </div>

        <div className="space-y-2">
          <div className="inline-flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <Sparkles className="w-3 h-3" />
            <span>Precision Nutrition</span>
          </div>

          <h1 className="text-xl font-black tracking-tight text-white pt-1">
            Você está sem conexão
          </h1>

          <p className="text-sm text-slate-400 leading-relaxed">
            Não foi possível alcançar o servidor do Precision Nutrition neste momento. Verifique sua internet e tente novamente.
          </p>
        </div>

        <button
          type="button"
          onClick={handleRetry}
          className="w-full inline-flex items-center justify-center gap-2 py-3 px-5 rounded-2xl bg-emerald-500 hover:bg-emerald-400 active:scale-[0.98] text-slate-950 font-bold text-sm transition-all shadow-lg shadow-emerald-500/20"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Tentar Novamente</span>
        </button>
      </div>
    </main>
  );
}
