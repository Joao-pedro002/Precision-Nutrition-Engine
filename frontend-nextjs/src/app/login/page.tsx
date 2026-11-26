import React from 'react';
import { Sparkles, ShieldCheck } from 'lucide-react';
import { LoginForm } from '@/components/LoginForm';

export default function LoginPage() {
  return (
    <main className="min-h-screen bg-[#020617] text-slate-100 flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md bg-slate-900/80 backdrop-blur border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl relative overflow-hidden space-y-6">
        <div className="absolute -top-24 -right-24 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

        {/* Identidade */}
        <div className="text-center space-y-3 relative z-10">
          <div className="flex justify-center">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-lg shadow-emerald-500/20">
              <div className="w-full h-full bg-slate-950 rounded-[14px] flex items-center justify-center">
                <Sparkles className="w-6 h-6 text-emerald-400" />
              </div>
            </div>
          </div>

          <div>
            <h1 className="text-xl font-black tracking-tight text-white">
              Precision Nutrition
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Acesso exclusivo para usuários autorizados
            </p>
          </div>
        </div>

        {/* Formulário de Login (sem cadastro público) */}
        <div className="relative z-10">
          <LoginForm />
        </div>

        {/* Rodapé informativo discreto abaixo do botão */}
        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-center gap-1.5 text-[11px] text-slate-500 relative z-10">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-500/70" />
          <span>Sessão segura persistente por 30 dias</span>
        </div>
      </div>
    </main>
  );
}
