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
  const isGoalReached = targetWaterMl > 0 && optimisticWater >= targetWaterMl;
  const remainingMl = Math.max(0, targetWaterMl - optimisticWater);

  return (
    <div className="h-full bg-slate-900/60 border border-slate-800 rounded-3xl p-5 shadow-xl relative overflow-hidden transition-all hover:border-slate-700 flex flex-col justify-between gap-4">
      {/* Glow azul no fundo */}
      <div className="absolute -bottom-16 -right-16 w-48 h-48 bg-sky-500/10 rounded-full blur-2xl pointer-events-none" />

      {/* Header empilhado e responsivo (sem conflito horizontal de Flexbox) */}
      <div className="space-y-3 relative z-10">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-9 h-9 rounded-2xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400 shrink-0">
              <Droplet className={`w-4 h-4 ${isPending ? 'animate-bounce' : ''}`} />
            </div>
            <h2 className="text-sm font-bold text-white tracking-tight truncate">
              Hidratação Diária
            </h2>
          </div>

          {isGoalReached ? (
            <span className="shrink-0 whitespace-nowrap inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
              <CheckCircle2 className="w-3 h-3" /> Meta Batida
            </span>
          ) : (
            <span className="shrink-0 whitespace-nowrap text-[11px] font-bold text-sky-300 bg-sky-500/10 px-2.5 py-0.5 rounded-full border border-sky-500/20 tabular-nums">
              {percent.toFixed(0)}% concluído
            </span>
          )}
        </div>

        {/* Quantidade consumida sem quebra vertical */}
        <div className="flex items-baseline justify-between gap-2 flex-wrap">
          <div className="flex items-baseline gap-1.5 whitespace-nowrap tabular-nums">
            <span className="text-2xl font-black text-sky-400">
              {optimisticWater.toFixed(0)}
            </span>
            <span className="text-xs text-slate-400 font-semibold">
              / {targetWaterMl.toFixed(0)} ml
            </span>
          </div>
          <p className="text-[11px] text-slate-400">
            {isGoalReached
              ? 'Balanço hídrico ideal.'
              : `Faltam ${remainingMl.toFixed(0)} ml`}
          </p>
        </div>

        {/* Barra de Progresso de Água Fluida */}
        <div className="relative w-full h-3 bg-slate-800 rounded-full overflow-hidden p-0.5">
          <div
            style={{ width: `${percent}%` }}
            className="h-full bg-gradient-to-r from-sky-500 to-cyan-400 rounded-full transition-all duration-300 ease-out shadow-[0_0_12px_rgba(56,189,248,0.5)]"
          />
        </div>
      </div>

      {/* Botões Rápidos de Ação (- 250 ml, + 250 ml, + 500 ml) sem quebra em mobile ou desktop */}
      <div className="grid grid-cols-3 gap-2 relative z-10">
        <button
          type="button"
          onClick={() => handleWaterDelta(-250)}
          disabled={optimisticWater <= 0}
          aria-label="Remover 250 ml de água"
          className="flex items-center justify-center gap-1 py-2.5 px-2 rounded-2xl bg-slate-800/80 hover:bg-slate-700/80 active:scale-95 text-xs font-semibold text-slate-300 border border-slate-700/60 transition-all disabled:opacity-40 disabled:pointer-events-none whitespace-nowrap"
        >
          <Minus className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <span>250 ml</span>
        </button>

        <button
          type="button"
          onClick={() => handleWaterDelta(250)}
          aria-label="Adicionar 250 ml de água"
          className="flex items-center justify-center gap-1 py-2.5 px-2 rounded-2xl bg-sky-500/15 hover:bg-sky-500/25 active:scale-95 text-xs font-bold text-sky-300 border border-sky-500/30 transition-all shadow-sm whitespace-nowrap"
        >
          <Plus className="w-3.5 h-3.5 text-sky-400 shrink-0" />
          <span>250 ml</span>
        </button>

        <button
          type="button"
          onClick={() => handleWaterDelta(500)}
          aria-label="Adicionar 500 ml de água"
          className="flex items-center justify-center gap-1 py-2.5 px-2 rounded-2xl bg-cyan-500/20 hover:bg-cyan-500/30 active:scale-95 text-xs font-bold text-cyan-200 border border-cyan-500/40 transition-all shadow-sm whitespace-nowrap"
        >
          <Plus className="w-3.5 h-3.5 text-cyan-300 shrink-0" />
          <span>500 ml</span>
        </button>
      </div>
    </div>
  );
}
