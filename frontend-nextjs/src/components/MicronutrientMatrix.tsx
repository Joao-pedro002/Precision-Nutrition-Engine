import React from 'react';
import { MicronutrientMatrixDto } from '@/types/nutrition';
import { Activity, HeartPulse } from 'lucide-react';

interface MicronutrientMatrixProps {
  matrix: MicronutrientMatrixDto;
}

export function MicronutrientMatrix({ matrix }: MicronutrientMatrixProps) {
  const { sodiumPotassiumRatio, sodiumPotassiumStatus, comparisons } = matrix;

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

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-6 shadow-xl space-y-6">
      {/* Header com Ícone e Título */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
            <Activity className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight">
              Matriz de Micronutrientes & Eletrólitos
            </h2>
            <p className="text-xs text-slate-400">
              Auditoria centesimal de vitaminas e minerais essenciais (9 chaves)
            </p>
          </div>
        </div>
      </div>

      {/* Destaque do Termômetro da Razão Na : K */}
      <div className="bg-slate-800/50 border border-slate-700/60 rounded-2xl p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <HeartPulse className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-300">
                Balanço Eletrolítico (Razão Na : K)
              </span>
              <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${ratioBadge.badge}`}>
                {ratioBadge.label}
              </span>
            </div>
            <p className="text-[11px] text-slate-400 mt-0.5">
              {sodiumPotassiumStatus}
            </p>
          </div>
        </div>

        <div className="flex items-baseline gap-2 self-end md:self-auto">
          <span className="text-xs text-slate-400 font-medium">Razão Ingerida:</span>
          <span className={`text-2xl font-black ${ratioBadge.textColor}`}>
            {sodiumPotassiumRatio !== null ? sodiumPotassiumRatio.toFixed(2) : '--'}
          </span>
          <span className="text-xs text-slate-400">
            (Alvo: &le; 0.60)
          </span>
        </div>
      </div>

      {/* Grade de 9 Micronutrientes */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
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
                <span className="text-[11px] font-semibold text-slate-400">
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

              <div className="flex items-baseline justify-between text-[11px] text-slate-400">
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
    </div>
  );
}
