'use client';

import React, { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { WeightHistoryResponse } from '@/types/nutrition';
import { recordWeightServerAction } from '@/app/actions';
import { Scale, TrendingDown, TrendingUp, Minus, Check, Loader2 } from 'lucide-react';

interface WeightTrackerProps {
  currentDate: string;
  weightHistory: WeightHistoryResponse | null;
}

export function WeightTracker({ currentDate, weightHistory }: WeightTrackerProps) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  const existingForDate = weightHistory?.history.find((h) => h.logDate === currentDate);
  const defaultInput = existingForDate
    ? existingForDate.weightKg.toFixed(1)
    : weightHistory?.latestWeightKg != null
    ? weightHistory.latestWeightKg.toFixed(1)
    : '';

  const [weightInput, setWeightInput] = useState<string>(defaultInput);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setFeedback(null);

    const parsed = parseFloat(weightInput.replace(',', '.'));
    if (!Number.isFinite(parsed) || parsed < 20 || parsed > 400) {
      setFeedback({ type: 'error', message: 'Informe um peso válido entre 20 e 400 kg.' });
      return;
    }

    startTransition(async () => {
      try {
        await recordWeightServerAction(currentDate, parsed);
        setFeedback({ type: 'success', message: `Peso ${parsed.toFixed(1)} kg salvo!` });
        router.refresh();
      } catch (err) {
        console.error('Erro ao salvar peso:', err);
        setFeedback({ type: 'error', message: 'Falha ao salvar peso no servidor.' });
      }
    });
  };

  const latestWeight = weightHistory?.latestWeightKg ?? null;
  const deltaKg = weightHistory?.deltaKg ?? null;

  const renderDeltaBadge = () => {
    if (deltaKg === null) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-slate-800 text-slate-400 border border-slate-700/70 whitespace-nowrap">
          <Minus className="w-3 h-3" />
          1ª pesagem
        </span>
      );
    }

    if (deltaKg < -0.005) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 whitespace-nowrap tabular-nums">
          <TrendingDown className="w-3 h-3 text-emerald-400" />
          {deltaKg.toFixed(1)} kg
        </span>
      );
    }

    if (deltaKg > 0.005) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30 whitespace-nowrap tabular-nums">
          <TrendingUp className="w-3 h-3 text-amber-400" />
          +{deltaKg.toFixed(1)} kg
        </span>
      );
    }

    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-slate-800 text-slate-300 border border-slate-700/70 whitespace-nowrap tabular-nums">
        <Minus className="w-3 h-3" />
        0.0 kg
      </span>
    );
  };

  return (
    <section
      aria-label="Rastreador de Peso Corporal"
      className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-lg transition-all hover:border-slate-700"
    >
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        {/* Esquerda: Ícone + Métrica Atual + Variação */}
        <div className="flex items-center gap-3.5 min-w-0">
          <div className="w-10 h-10 rounded-2xl bg-violet-500/10 border border-violet-500/20 flex items-center justify-center text-violet-400 shrink-0">
            <Scale className="w-5 h-5" />
          </div>

          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-sm font-bold text-white tracking-tight">Peso Corporal</h2>
              {latestWeight !== null && renderDeltaBadge()}
            </div>

            <div className="flex items-baseline gap-2 mt-0.5 flex-wrap">
              {latestWeight !== null ? (
                <>
                  <span className="text-xl font-black text-violet-300 tabular-nums whitespace-nowrap">
                    {latestWeight.toFixed(1)} kg
                  </span>
                  <span className="text-xs text-slate-400">
                    {weightHistory?.latestLogDate === currentDate
                      ? 'Registrado nesta data'
                      : `Última pesagem em ${weightHistory?.latestLogDate}`}
                  </span>
                </>
              ) : (
                <span className="text-xs text-slate-400">
                  Nenhum peso registrado ainda. Informe sua pesagem do dia.
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Direita: Formulário Compacto de Registro */}
        <form onSubmit={handleSubmit} className="flex items-center gap-2 w-full md:w-auto">
          <div className="relative flex-1 md:w-36">
            <label htmlFor="daily-weight-input" className="sr-only">
              Peso corporal em kg
            </label>
            <input
              id="daily-weight-input"
              type="number"
              inputMode="decimal"
              step="0.1"
              min="20"
              max="400"
              placeholder="ex: 68.0"
              value={weightInput}
              onChange={(e) => setWeightInput(e.target.value)}
              disabled={isPending}
              className="w-full bg-slate-950/70 border border-slate-700/80 focus:border-violet-400 focus:outline-none rounded-xl py-2 pl-3 pr-9 text-sm font-bold text-white tabular-nums placeholder:text-slate-500 transition-colors"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400 pointer-events-none">
              kg
            </span>
          </div>

          <button
            type="submit"
            disabled={isPending || !weightInput.trim()}
            className="inline-flex items-center justify-center gap-1.5 py-2 px-4 rounded-xl bg-violet-500/20 hover:bg-violet-500/30 active:scale-95 text-xs font-bold text-violet-200 border border-violet-500/40 transition-all disabled:opacity-40 disabled:pointer-events-none whitespace-nowrap"
          >
            {isPending ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Check className="w-3.5 h-3.5" />
            )}
            <span>{isPending ? 'Salvando...' : 'Salvar Peso'}</span>
          </button>
        </form>
      </div>

      {feedback && (
        <p
          role="status"
          className={`mt-2.5 text-xs font-medium ${
            feedback.type === 'error' ? 'text-rose-400' : 'text-emerald-400'
          }`}
        >
          {feedback.message}
        </p>
      )}
    </section>
  );
}
