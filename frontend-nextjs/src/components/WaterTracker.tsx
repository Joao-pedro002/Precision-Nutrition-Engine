'use client';

import React, { useOptimistic, useTransition } from 'react';
import { updateWaterServerAction } from '@/app/actions';
import { Droplet, Plus, Minus, CheckCircle2 } from 'lucide-react';

interface WaterTrackerProps {
  currentDate: string;
  initialWaterMl: number;
  targetWaterMl: number;
}

export function WaterTracker({ currentDate, initialWaterMl, targetWaterMl }: WaterTrackerProps) {
  const [isPending, startTransition] = useTransition();

  // Optimistic UI: atualiza a tela no milissegundo do clique
  const [optimisticWater, setOptimisticWater] = useOptimistic(
    initialWaterMl,
    (current: number, delta: number) => Math.max(0, current + delta)
  );

  const handleWaterDelta = (deltaMl: number) => {
    startTransition(async () => {
      setOptimisticWater(deltaMl);
      try {
        await updateWaterServerAction(currentDate, 'INCREMENT', deltaMl);
      } catch (err) {
        console.error('Falha ao sincronizar hidratação com o servidor:', err);
      }
    });
  };

  const percent = targetWaterMl > 0 ? Math.min(100, (optimisticWater / targetWaterMl) * 100) : 0;
  const isGoalReached = optimisticWater >= targetWaterMl;
  const remainingMl = Math.max(0, targetWaterMl - optimisticWater);

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-6 shadow-xl relative overflow-hidden transition-all hover:border-slate-700">
      {/* Glow azul no fundo */}
      <div className="absolute -bottom-16 -right-16 w-52 h-52 bg-sky-500/10 rounded-full blur-2xl pointer-events-none" />

      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400">
            <Droplet className={`w-5 h-5 ${isPending ? 'animate-bounce' : ''}`} />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight flex items-center gap-2">
              Hidratação Diária
              {isGoalReached && (
                <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
                  <CheckCircle2 className="w-3 h-3" /> Meta Batida
                </span>
              )}
            </h2>
            <p className="text-xs text-slate-400">
              {isGoalReached
                ? 'Excelente! Balanço hídrico ideal atingido.'
                : `Faltam ${remainingMl.toFixed(0)} ml para a meta de hidratação.`}
            </p>
          </div>
        </div>

        {/* Quantidade consumida */}
        <div className="text-right">
          <div className="flex items-baseline gap-1 justify-end">
            <span className="text-2xl font-black text-sky-400">
              {optimisticWater.toFixed(0)}
            </span>
            <span className="text-xs text-slate-400 font-semibold">
              / {targetWaterMl.toFixed(0)} ml
            </span>
          </div>
          <span className="text-[11px] font-bold text-slate-400">
            {percent.toFixed(0)}% concluído
          </span>
        </div>
      </div>

      {/* Barra de Progresso de Água Fluida */}
      <div className="relative w-full h-3.5 bg-slate-800 rounded-full overflow-hidden p-0.5 mb-6">
        <div
          style={{ width: `${percent}%` }}
          className="h-full bg-gradient-to-r from-sky-500 to-cyan-400 rounded-full transition-all duration-300 ease-out shadow-[0_0_12px_rgba(56,189,248,0.5)]"
        />
      </div>

      {/* Botões Rápidos de Ação (+250ml, +500ml, -250ml) */}
      <div className="grid grid-cols-3 gap-3">
        <button
          type="button"
          onClick={() => handleWaterDelta(-250)}
          disabled={optimisticWater <= 0}
          className="flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-2xl bg-slate-800/80 hover:bg-slate-700/80 active:scale-95 text-xs font-semibold text-slate-300 border border-slate-700/60 transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          <Minus className="w-3.5 h-3.5 text-slate-400" />
          <span>250 ml</span>
        </button>

        <button
          type="button"
          onClick={() => handleWaterDelta(250)}
          className="flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-2xl bg-sky-500/15 hover:bg-sky-500/25 active:scale-95 text-xs font-bold text-sky-300 border border-sky-500/30 transition-all shadow-sm"
        >
          <Plus className="w-3.5 h-3.5 text-sky-400" />
          <span>250 ml (Copo)</span>
        </button>

        <button
          type="button"
          onClick={() => handleWaterDelta(500)}
          className="flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-2xl bg-cyan-500/20 hover:bg-cyan-500/30 active:scale-95 text-xs font-bold text-cyan-200 border border-cyan-500/40 transition-all shadow-sm"
        >
          <Plus className="w-3.5 h-3.5 text-cyan-300" />
          <span>500 ml (Garrafa)</span>
        </button>
      </div>
    </div>
  );
}
