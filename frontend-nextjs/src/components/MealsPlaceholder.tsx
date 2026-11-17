import React from 'react';
import { MealDashboard } from '@/types/nutrition';
import { Utensils, Clock, CheckCircle2 } from 'lucide-react';

interface MealsPlaceholderProps {
  meals: MealDashboard[];
}

export function MealsPlaceholder({ meals }: MealsPlaceholderProps) {
  const completedCount = meals.filter((m) => m.isCompleted).length;

  return (
    <div className="bg-slate-900/40 border border-slate-800/80 rounded-3xl p-6 shadow-xl space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <Utensils className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight flex items-center gap-2">
              Plano de Refeições Prescritas
              <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-slate-800 text-slate-300">
                {completedCount} / {meals.length} realizadas
              </span>
            </h2>
            <p className="text-xs text-slate-400">
              5 refeições configuradas com suporte a Multi-Opção (Opção 1 & 2) e Substituição Equivalente
            </p>
          </div>
        </div>

        <span className="text-xs font-medium text-emerald-400/80 bg-emerald-500/10 px-3 py-1 rounded-full border border-emerald-500/20 hidden sm:inline-block">
          Etapa 6: Cards Expansíveis & Modal
        </span>
      </div>

      {/* Pré-visualização resumida das 5 refeições */}
      <div className="grid grid-cols-1 sm:grid-cols-5 gap-3 pt-2">
        {meals.map((meal) => (
          <div
            key={meal.id}
            className={`p-3.5 rounded-2xl border transition-all ${
              meal.isCompleted
                ? 'bg-emerald-500/5 border-emerald-500/30 text-emerald-200'
                : 'bg-slate-800/30 border-slate-800 text-slate-300'
            }`}
          >
            <div className="flex items-center justify-between text-xs mb-1">
              <span className="flex items-center gap-1 font-semibold text-slate-400">
                <Clock className="w-3 h-3 text-slate-500" />
                {meal.targetTime ? meal.targetTime.slice(0, 5) : '--:--'}
              </span>
              {meal.isCompleted && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />}
            </div>
            <h3 className="text-xs font-bold truncate text-white">{meal.name}</h3>
            <p className="text-[11px] text-slate-400 mt-1">
              {meal.options.length} opções disponíveis
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
