'use client';

import React, { useTransition } from 'react';
import { logoutServerAction } from '@/app/actions';
import { LogOut, Loader2 } from 'lucide-react';

export function LogoutButton() {
  const [isPending, startTransition] = useTransition();

  const handleLogout = () => {
    startTransition(async () => {
      await logoutServerAction();
    });
  };

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={isPending}
      title="Encerrar sessão"
      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-2xl bg-slate-900/60 hover:bg-rose-500/15 border border-slate-800 hover:border-rose-500/30 text-xs font-semibold text-slate-300 hover:text-rose-300 transition-colors disabled:opacity-50 whitespace-nowrap"
    >
      {isPending ? (
        <Loader2 className="w-3.5 h-3.5 animate-spin text-rose-400" />
      ) : (
        <LogOut className="w-3.5 h-3.5 text-slate-400" />
      )}
      <span>{isPending ? 'Saindo...' : 'Sair'}</span>
    </button>
  );
}
