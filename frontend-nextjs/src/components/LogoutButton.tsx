'use client';

import React, { useTransition } from 'react';
import { logoutServerAction } from '@/app/actions';
import { LogOut, Loader2 } from 'lucide-react';

interface LogoutButtonProps {
  compact?: boolean;
  className?: string;
}

export function LogoutButton({ compact = false, className = '' }: LogoutButtonProps) {
  const [isPending, startTransition] = useTransition();

  const handleLogout = () => {
    startTransition(async () => {
      await logoutServerAction();
    });
  };

  if (compact) {
    return (
      <button
        type="button"
        onClick={handleLogout}
        disabled={isPending}
        title="Encerrar sessão"
        aria-label={isPending ? 'Encerrando sessão' : 'Sair da conta'}
        className={`inline-flex items-center justify-center min-w-[44px] min-h-[44px] px-2.5 rounded-xl bg-slate-900/70 hover:bg-rose-500/15 border border-slate-800 hover:border-rose-500/30 text-slate-300 hover:text-rose-300 transition-colors disabled:opacity-50 shrink-0 ${className}`}
      >
        {isPending ? (
          <Loader2 className="w-4 h-4 animate-spin text-rose-400" aria-hidden="true" />
        ) : (
          <LogOut className="w-4 h-4 text-slate-400" aria-hidden="true" />
        )}
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={isPending}
      title="Encerrar sessão"
      className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-2xl bg-slate-900/60 hover:bg-rose-500/15 border border-slate-800 hover:border-rose-500/30 text-xs font-semibold text-slate-300 hover:text-rose-300 transition-colors disabled:opacity-50 whitespace-nowrap ${className}`}
    >
      {isPending ? (
        <Loader2 className="w-3.5 h-3.5 animate-spin text-rose-400" aria-hidden="true" />
      ) : (
        <LogOut className="w-3.5 h-3.5 text-slate-400" aria-hidden="true" />
      )}
      <span>{isPending ? 'Saindo...' : 'Sair'}</span>
    </button>
  );
}
