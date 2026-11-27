'use client';

import React, { useId, useState } from 'react';
import { MicronutrientMatrixDto } from '@/types/nutrition';
import { Activity, ChevronDown, HeartPulse } from 'lucide-react';

interface MicronutrientMatrixProps {
  matrix: MicronutrientMatrixDto;
}

export function MicronutrientMatrix({ matrix }: MicronutrientMatrixProps) {
  const { sodiumPotassiumRatio, sodiumPotassiumStatus, comparisons } = matrix;
  const [isExpanded, setIsExpanded] = useState(false);
  const detailsId = useId();
  const headingId = useId();

  // Cor e badge da razão Sódio : Potássio
  const getRatioBadge = () => {
    if (sodiumPotassiumRatio === null) {
      return {
        badge: 'bg-slate-800 text-slate-400 border-slate-700',
        textColor: 'text-slate-400',
        label: 'Sem dados suficientes',
      };
    }
    if (sodiumPotassiumRatio <= 0.6) {
      return {
        badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
        textColor: 'text-emerald-400',
        label: 'Excelente (Cardioprotetor)',
      };
    }
    if (sodiumPotassiumRatio <= 1.0) {
      return {
        badge: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
        textColor: 'text-sky-400',
        label: 'Adequado (Na:K <= 1.0)',
      };
    }
    return {
      badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
      textColor: 'text-amber-400',
      label: 'Atenção (Excesso de Sódio)',
    };
  };

  const ratioBadge = getRatioBadge();
  const nutrientCount = comparisons.length;

  return (
    <section
      aria-labelledby={headingId}
      className="bg-slate-900/60 border border-slate-800 rounded-3xl p-4 sm:p-6 shadow-xl space-y-4 sm:space-y-6"
    >
      {/* Header com Ícone e Título */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 shrink-0">
            <Activity className="w-5 h-5" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <h2 id={headingId} className="text-base font-bold text-white tracking-tight [text-wrap:balance]">
              Matriz de Micronutrientes & Eletrólitos
            </h2>
            <p className="text-xs text-slate-400 leading-snug">
              Auditoria centesimal de vitaminas e minerais essenciais ({nutrientCount} chaves)
            </p>
          </div>
        </div>
      </div>

      {/* Destaque do Termômetro da Razão Na : K */}
      <div className="bg-slate-800/50 border border-slate-700/60 rounded-2xl p-3.5 sm:p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-3 sm:gap-4">
        <div className="flex items-start sm:items-center gap-3 min-w-0">
          <div className="p-2.5 rounded-xl bg-rose-500/10 text-rose-400 border border-rose-500/20 shrink-0">
            <HeartPulse className="w-5 h-5" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-xs font-semibold text-slate-300">
                Balanço Eletrolítico (Razão Na : K)
              </span>
              <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border whitespace-nowrap ${ratioBadge.badge}`}>
                {ratioBadge.label}
              </span>
            </div>
            <p className="text-[11px] text-slate-400 mt-0.5 leading-snug">
              {sodiumPotassiumStatus}
            </p>
          </div>
        </div>

        <div className="flex items-baseline gap-2 self-end md:self-auto whitespace-nowrap">
          <span className="text-xs text-slate-400 font-medium">Razão Ingerida:</span>
          <span className={`text-2xl font-black tabular-nums ${ratioBadge.textColor}`}>
            {sodiumPotassiumRatio !== null ? sodiumPotassiumRatio.toFixed(2) : '--'}
          </span>
          <span className="text-xs text-slate-400">
            (Alvo: &le; 0.60)
          </span>
        </div>
      </div>

      {/* Controle de Disclosure no Mobile (Recolhido por padrão < md, sempre visível em md+) */}
      <button
        type="button"
        onClick={() => setIsExpanded((prev) => !prev)}
        aria-expanded={isExpanded}
        aria-controls={detailsId}
        className="md:hidden w-full min-h-[44px] inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl bg-slate-800/70 hover:bg-slate-800 border border-slate-700/70 text-xs font-semibold text-slate-200 transition-colors"
      >
        <ChevronDown
          className={`w-4 h-4 text-indigo-400 shrink-0 transition-transform duration-200 motion-reduce:transition-none ${
            isExpanded ? 'rotate-180' : 'rotate-0'
          }`}
          aria-hidden="true"
        />
        <span>
          {isExpanded
            ? `Ocultar detalhes (${nutrientCount} micronutrientes)`
            : `Ver detalhes (${nutrientCount} micronutrientes)`}
        </span>
      </button>

      {/* Grade de 9 Micronutrientes */}
      <div
        id={detailsId}
        className={`${isExpanded ? 'grid' : 'hidden md:grid'} grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3`}
      >
        {comparisons.map((item) => {
          const percent = Math.min(100, item.percentageOfTarget);
          const isComplete = item.percentageOfTarget >= 100;

          return (
            <div
              key={item.nutrientKey}
              className="bg-slate-800/30 border border-slate-800/80 rounded-2xl p-3.5 flex flex-col justify-between hover:border-slate-700 transition-colors"
            >
              <div className="flex items-center justify-between gap-2 mb-2">
                <span className="text-xs font-bold text-slate-200">
                  {item.displayName}
                </span>
                <span className="text-[11px] font-semibold text-slate-400 tabular-nums">
                  {item.percentageOfTarget.toFixed(0)}%
                </span>
              </div>

              {/* Barra de Progresso do Micronutriente */}
              <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden mb-2">
                <div
                  style={{ width: `${percent}%` }}
                  className={`h-full rounded-full transition-all duration-500 ${
                    isComplete ? 'bg-emerald-400' : 'bg-indigo-400'
                  }`}
                />
              </div>

              <div className="flex items-baseline justify-between text-[11px] text-slate-400 tabular-nums">
                <span className="font-semibold text-slate-300">
                  {item.consumedAmount.toFixed(1)} {item.unit}
                </span>
                <span>
                  Meta: {item.targetAmount.toFixed(0)} {item.unit}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
