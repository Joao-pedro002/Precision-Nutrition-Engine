import React from 'react';
import { DailySummaryDto } from '@/types/nutrition';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';

interface MacroDonutChartsProps {
  summary: DailySummaryDto;
}

interface SingleMacroRingProps {
  label: string;
  shortCode: string;
  consumed: number;
  target: number;
  percentage: number;
  colorHex: string;
  textClass: string;
  badgeBgClass: string;
}

function formatGrams(value: number): string {
  const safe = Number.isFinite(value) ? value : 0;
  return Math.abs(safe - Math.round(safe)) < 0.05
    ? Math.round(safe).toString()
    : safe.toFixed(1);
}

function SingleMacroRing({
  label,
  shortCode,
  consumed,
  target,
  percentage,
  colorHex,
  textClass,
  badgeBgClass,
}: SingleMacroRingProps) {
  const safeConsumed = Number.isFinite(consumed) ? consumed : 0;
  const safeTarget = Number.isFinite(target) ? target : 0;
  const computedPercent =
    Number.isFinite(percentage) && percentage >= 0
      ? percentage
      : safeTarget > 0
      ? (safeConsumed / safeTarget) * 100
      : 0;

  const clampedPercent = Math.min(100, Math.max(0, computedPercent));
  const delta = safeTarget - safeConsumed;
  const isExceeded = safeTarget > 0 && delta < -0.05;
  const isGoalMet = safeTarget > 0 && Math.abs(delta) <= 0.05;
  const excessAmount = Math.abs(delta);
  const remainingAmount = Math.max(0, delta);

  const ringColor = isExceeded ? '#f43f5e' : colorHex;
  const statusAriaText = isExceeded
    ? `${label}: ${formatGrams(safeConsumed)}g de ${formatGrams(safeTarget)}g (${computedPercent.toFixed(0)}%), +${formatGrams(excessAmount)}g acima da meta`
    : isGoalMet
    ? `${label}: ${formatGrams(safeConsumed)}g de ${formatGrams(safeTarget)}g (100%), meta atingida`
    : `${label}: ${formatGrams(safeConsumed)}g de ${formatGrams(safeTarget)}g (${computedPercent.toFixed(0)}%), faltam ${formatGrams(remainingAmount)}g`;

  const ringStyle = {
    '--value': clampedPercent,
    '--fill-color': ringColor,
  } as React.CSSProperties;

  return (
    <div
      className={`bg-slate-900/60 border rounded-3xl p-5 flex flex-col items-center shadow-lg transition-all ${
        isExceeded
          ? 'border-rose-500/50 shadow-rose-500/10'
          : 'border-slate-800 hover:border-slate-700'
      }`}
    >
      <div className="w-full flex items-center justify-between mb-3">
        <span className={`text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-lg border ${badgeBgClass}`}>
          {shortCode}
        </span>
        <span className="text-xs font-bold text-slate-200">{label}</span>
      </div>

      {/* Anel Semântico de Progresso (<progress> + conic-gradient) */}
      <div className="macro-progress-ring-wrapper">
        <progress
          value={clampedPercent}
          max={100}
          aria-label={`Progresso de ${label}`}
          aria-valuetext={statusAriaText}
          className="macro-progress-ring"
          style={ringStyle}
        />

        {/* Informação central oculta de leitores de tela para evitar duplicidade com <progress> */}
        <div
          aria-hidden="true"
          className="absolute flex flex-col items-center justify-center text-center pointer-events-none"
        >
          <span className={`text-xl font-black tabular-nums ${isExceeded ? 'text-rose-400' : textClass}`}>
            {computedPercent.toFixed(0)}%
          </span>
          <span className="text-[10px] text-slate-400 font-medium">da meta</span>
        </div>
      </div>

      <div className="mt-4 text-center w-full space-y-2">
        <div className="flex items-baseline justify-center gap-1.5 tabular-nums">
          <span className="text-lg font-black text-slate-100">
            {formatGrams(safeConsumed)}g
          </span>
          <span className="text-xs text-slate-400 font-medium">
            / {formatGrams(safeTarget)}g
          </span>
        </div>

        {/* Saldo Restante / Alerta de Excesso */}
        <div className="flex justify-center">
          {isExceeded ? (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold bg-rose-500/15 text-rose-300 border border-rose-500/30">
              <AlertTriangle className="w-3 h-3 text-rose-400 shrink-0" />
              +{formatGrams(excessAmount)}g acima da meta
            </span>
          ) : isGoalMet ? (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
              <CheckCircle2 className="w-3 h-3 text-emerald-400 shrink-0" />
              Meta atingida!
            </span>
          ) : (
            <span className="text-[11px] font-medium text-slate-400">
              Faltam <strong className="text-slate-200">{formatGrams(remainingAmount)}g</strong>
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

export function MacroDonutCharts({ summary }: MacroDonutChartsProps) {
  const {
    consumedProteinG = 0,
    targetProteinG = 0,
    proteinPercentage = 0,
    consumedCarbohydrateG = 0,
    targetCarbohydrateG = 0,
    carbohydratePercentage = 0,
    consumedLipidG = 0,
    targetLipidG = 0,
    lipidPercentage = 0,
    consumedFiberG = 0,
    targetFiberG = 0,
  } = summary;

  const fiberPercent = targetFiberG > 0 ? Math.min(100, (consumedFiberG / targetFiberG) * 100) : 0;

  return (
    <section aria-label="Painel Global de Macronutrientes" className="space-y-4">
      {/* Grid com os 3 Anéis de Macronutrientes */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <SingleMacroRing
          label="Proteínas"
          shortCode="P"
          consumed={consumedProteinG}
          target={targetProteinG}
          percentage={proteinPercentage}
          colorHex="#06b6d4"
          textClass="text-cyan-400"
          badgeBgClass="bg-cyan-500/10 text-cyan-400 border-cyan-500/20"
        />

        <SingleMacroRing
          label="Carboidratos"
          shortCode="C"
          consumed={consumedCarbohydrateG}
          target={targetCarbohydrateG}
          percentage={carbohydratePercentage}
          colorHex="#f59e0b"
          textClass="text-amber-400"
          badgeBgClass="bg-amber-500/10 text-amber-400 border-amber-500/20"
        />

        <SingleMacroRing
          label="Gorduras"
          shortCode="G"
          consumed={consumedLipidG}
          target={targetLipidG}
          percentage={lipidPercentage}
          colorHex="#f43f5e"
          textClass="text-rose-400"
          badgeBgClass="bg-rose-500/10 text-rose-400 border-rose-500/20"
        />
      </div>

      {/* Barra de Fibras Complementar */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 font-bold text-xs">
            F
          </div>
          <div>
            <h2 className="text-xs font-semibold text-slate-300">Fibras Alimentares</h2>
            <p className="text-xs text-slate-400">
              Essencial para motilidade intestinal e controle glicêmico
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          <div className="w-full sm:w-36 h-2 bg-slate-800 rounded-full overflow-hidden">
            <div
              style={{ width: `${fiberPercent}%` }}
              className="h-full bg-emerald-400 rounded-full transition-all duration-500"
            />
          </div>
          <span className="text-xs font-bold text-slate-200 whitespace-nowrap tabular-nums">
            {formatGrams(consumedFiberG)} / {formatGrams(targetFiberG)}g
          </span>
        </div>
      </div>
    </section>
  );
}
