'use client';

import React, { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { loginServerAction } from '@/app/actions';
import { Eye, EyeOff, Lock, Mail, Loader2, AlertCircle, LogIn } from 'lucide-react';

export function LoginForm() {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setErrorMessage(null);

    startTransition(async () => {
      try {
        const result = await loginServerAction(email.trim(), password);
        if (!result.success) {
          setErrorMessage(result.error || 'E-mail ou senha inválidos.');
          return;
        }
        router.push('/');
        router.refresh();
      } catch {
        setErrorMessage('Não foi possível conectar ao servidor. Tente novamente.');
      }
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-1.5">
        <label htmlFor="email" className="block text-xs font-bold text-slate-300">
          E-mail
        </label>
        <div className="relative">
          <Mail className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="username"
            spellCheck={false}
            enterKeyHint="next"
            required
            placeholder="voce@exemplo.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={isPending}
            className="w-full pl-10 pr-4 py-3 bg-slate-950/80 border border-slate-700/80 focus:border-emerald-400 focus:outline-none rounded-2xl text-sm font-medium text-white placeholder:text-slate-500 transition-colors disabled:opacity-50"
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <label htmlFor="password" className="block text-xs font-bold text-slate-300">
          Senha
        </label>
        <div className="relative">
          <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            id="password"
            name="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            enterKeyHint="done"
            required
            placeholder="Digite sua senha"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isPending}
            className="w-full pl-10 pr-11 py-3 bg-slate-950/80 border border-slate-700/80 focus:border-emerald-400 focus:outline-none rounded-2xl text-sm font-medium text-white placeholder:text-slate-500 transition-colors disabled:opacity-50"
          />
          <button
            type="button"
            onClick={() => setShowPassword((prev) => !prev)}
            aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
            aria-pressed={showPassword}
            className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-slate-400 hover:text-white transition-colors"
          >
            {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {errorMessage && (
        <div
          role="alert"
          className="p-3 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300 flex items-center gap-2"
        >
          <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      <button
        type="submit"
        disabled={isPending}
        className="w-full py-3.5 px-5 rounded-2xl bg-emerald-500 hover:bg-emerald-400 active:scale-[0.98] text-slate-950 font-black text-sm shadow-lg shadow-emerald-500/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
      >
        {isPending ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            <span>Entrando...</span>
          </>
        ) : (
          <>
            <LogIn className="w-4 h-4" />
            <span>Entrar</span>
          </>
        )}
      </button>
    </form>
  );
}
