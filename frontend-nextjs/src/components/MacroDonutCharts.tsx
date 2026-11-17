import React from 'react';
import { DailySummaryDto } from '@/types/nutrition';

interface MacroDonutChartsProps {
  summary: DailySummaryDto;
}

interface SingleDonutProps {
  label: string;
  consumed: number;
  target: number;
  percentage: number;
  colorHex: string;
  gradientId: string;
  strokeClass: string;
  textClass: string;
  bgStrokeClass: string;
}

function SingleDonut({
  label,
  consumed,
  target,
  percentage,
  gradientId,
  strokeClass,
  textClass,
  bgStrokeClass,
}: SingleDonutProps) {
  const radius = 42;
  const circumference = 2 * Math.PI * radius;
  const clampedPercent = Math.min(100, Math.max(0, percentage));
  const strokeDashoffset = circumference - (circumference * clampedPercent) / 100;
  const remaining = Math.max(0, target - consumed);

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-5 flex flex-col items-center shadow-lg transition-all hover:border-slate-700">
      <div className="relative w-32 h-32 flex items-center justify-center">
        <svg className="w-full h-full -rotate-90 transform" viewBox="0 0 100 100">
          <circle
            cx="50"
            cy="50"
            r={radius}
            className={bgStrokeClass}
            strokeWidth="9"
            fill="transparent"
          />
          <circle
            cx="50"
            cy="50"
            r={radius}
            stroke={`url(#${gradientId})`}
            strokeWidth="9"
            fill="transparent"
            strokeDasharray={circumference}
            strokeDashoffset={strokeDashoffset}
            strokeLinecap="round"
            className="transition-all duration-700 ease-out"
          />
          <defs>
            <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" className={strokeClass} stopOpacity="1" />
              <stop offset="100%" className={strokeClass} stopOpacity="0.75" />
            </linearGradient>
          </defs>
        </svg>

        {/* Informação Centralizada dentro do Donut */}
        <div className="absolute flex flex-col items-center justify-center text-center">
          <span className={`text-xl font-black ${textClass}`}>
            {percentage.toFixed(0)}%
          </span>
          <span className="text-[10px] text-slate-400 font-medium">da meta</span>
        </div>
      </div>

      <div className="mt-4 text-center w-full">
        <h2 className="text-sm font-bold text-white tracking-wide">{label}</h2>
        <div className="mt-1 flex items-baseline justify-center gap-1.5">
          <span className="text-lg font-black text-slate-100">
            {consumed.toFixed(1)}g
          </span>
          <span className="text-xs text-slate-400 font-medium">
            / {target.toFixed(0)}g
          </span>
        </div>
        <p className="text-[11px] text-slate-400 mt-0.5">
          {remaining > 0 ? `${remaining.toFixed(1)}g restantes` : 'Meta batida!'}
        </p>
      </div>
    </div>
  );
}

export function MacroDonutCharts({ summary }: MacroDonutChartsProps) {
  const {
    consumedProteinG,
    targetProteinG,
    proteinPercentage,
    consumedCarbohydrateG,
    targetCarbohydrateG,
    carbohydratePercentage,
    consumedLipidG,
    targetLipidG,
    lipidPercentage,
    consumedFiberG,
    targetFiberG,
  } = summary;

  const fiberPercent = targetFiberG > 0 ? Math.min(100, (consumedFiberG / targetFiberG) * 100) : 0;

  return (
    <div className="space-y-4">
      {/* Grid com os 3 Gráficos de Anel */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <SingleDonut
          label="Proteínas"
          consumed={consumedProteinG}
          target={targetProteinG}
          percentage={proteinPercentage}
          colorHex="#06b6d4"
          gradientId="grad-protein"
          strokeClass="text-cyan-400"
          textClass="text-cyan-400"
          bgStrokeClass="stroke-slate-800"
        />

        <SingleDonut
          label="Carboidratos"
          consumed={consumedCarbohydrateG}
          target={targetCarbohydrateG}
          percentage={carbohydratePercentage}
          colorHex="#f59e0b"
          gradientId="grad-carbs"
          strokeClass="text-amber-400"
          textClass="text-amber-400"
          bgStrokeClass="stroke-slate-800"
        />

        <SingleDonut
          label="Gorduras"
          consumed={consumedLipidG}
          target={targetLipidG}
          percentage={lipidPercentage}
          colorHex="#f43f5e"
          gradientId="grad-lipids"
          strokeClass="text-rose-400"
          textClass="text-rose-400"
          bgStrokeClass="stroke-slate-800"
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
          <span className="text-xs font-bold text-slate-200 whitespace-nowrap">
            {consumedFiberG.toFixed(1)} / {targetFiberG.toFixed(0)}g
          </span>
        </div>
      </div>
    </div>
  );
}
