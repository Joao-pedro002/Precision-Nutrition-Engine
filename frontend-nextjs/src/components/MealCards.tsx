'use client';

import React, { useState, useOptimistic, useTransition } from 'react';
import { MealDashboard, MealItemCalculated } from '@/types/nutrition';
import { checkMealServerAction } from '@/app/actions';
import { SubstitutionModal } from '@/components/SubstitutionModal';
import { OffDietModal } from '@/components/OffDietModal';
import {
  Utensils,
  Clock,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  ArrowRightLeft,
  UtensilsCrossed,
  Sparkles,
  Loader2,
} from 'lucide-react';

interface MealCardsProps {
  meals: MealDashboard[];
  currentDate: string;
}

interface SingleMealCardProps {
  meal: MealDashboard;
  currentDate: string;
  onOpenSubstitution: (mealId: string, item: MealItemCalculated, optionNumber: number) => void;
  onOpenOffDiet: (mealName: string) => void;
}

function SingleMealCard({
  meal,
  currentDate,
  onOpenSubstitution,
  onOpenOffDiet,
}: SingleMealCardProps) {
  const [isExpanded, setIsExpanded] = useState<boolean>(true);
  const [activeOptionNumber, setActiveOptionNumber] = useState<number>(
    meal.completedOptionNumber || 1
  );

  const [isPending, startTransition] = useTransition();

  // Optimistic UI para o Check de refeição ("Na Dieta")
  const [optimisticState, setOptimisticState] = useOptimistic(
    {
      isCompleted: meal.isCompleted,
      completedOptionNumber: meal.completedOptionNumber,
    },
    (current, newOptionNumber: number) => {
      // Toggle off se clicou na mesma opção já completada
      if (current.isCompleted && current.completedOptionNumber === newOptionNumber) {
        return { isCompleted: false, completedOptionNumber: null };
      }
      return { isCompleted: true, completedOptionNumber: newOptionNumber };
    }
  );

  const handleToggleCheck = () => {
    startTransition(async () => {
      setOptimisticState(activeOptionNumber);
      try {
        await checkMealServerAction(currentDate, meal.id, activeOptionNumber);
      } catch (err) {
        console.error('Erro ao atualizar checagem de refeição:', err);
      }
    });
  };

  // Encontra a opção ativa selecionada no card
  const activeOption = meal.options.find((o) => o.optionNumber === activeOptionNumber) || meal.options[0];
  const isThisOptionCompleted =
    optimisticState.isCompleted && optimisticState.completedOptionNumber === activeOptionNumber;

  return (
    <div
      className={`rounded-3xl border transition-all duration-300 overflow-hidden shadow-lg ${
        optimisticState.isCompleted
          ? 'bg-emerald-950/20 border-emerald-500/40'
          : 'bg-slate-900/60 border-slate-800 hover:border-slate-700/80'
      }`}
    >
      {/* Top Accordion Header */}
      <div
        onClick={() => setIsExpanded(!isExpanded)}
        className="p-5 flex items-center justify-between cursor-pointer select-none bg-slate-900/40 hover:bg-slate-800/30 transition-colors"
      >
        <div className="flex items-center gap-3.5 min-w-0">
          <div
            className={`w-10 h-10 rounded-2xl flex items-center justify-center transition-colors shrink-0 ${
              optimisticState.isCompleted
                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                : 'bg-slate-800 text-slate-400 border border-slate-700'
            }`}
          >
            {optimisticState.isCompleted ? (
              <CheckCircle2 className="w-5 h-5 text-emerald-400" />
            ) : (
              <Utensils className="w-4 h-4" />
            )}
          </div>

          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="text-base font-bold text-white tracking-tight truncate">
                {meal.name}
              </h3>
              {optimisticState.isCompleted && (
                <span className="text-[10px] font-black uppercase px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shrink-0">
                  Opção {optimisticState.completedOptionNumber} Realizada
                </span>
              )}
            </div>

            <div className="flex items-center gap-2 mt-0.5 text-xs text-slate-400">
              <span className="flex items-center gap-1 font-medium">
                <Clock className="w-3 h-3 text-slate-500" />
                {meal.targetTime ? meal.targetTime.slice(0, 5) : '--:--'}
              </span>
              <span>•</span>
              <span className="font-semibold text-slate-300">
                {activeOption?.totalEnergyKcal.toFixed(0)} kcal
              </span>
              <span>•</span>
              <span>P: {activeOption?.totalProteinG.toFixed(1)}g</span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            className="p-1 rounded-xl text-slate-400 hover:text-white"
            aria-label="Expandir ou recolher"
          >
            {isExpanded ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Expanded Accordion Body */}
      {isExpanded && activeOption && (
        <div className="p-5 pt-0 space-y-4 border-t border-slate-800/40">
          {/* Alternador de Opções (Opção 1 vs Opção 2) e Ações Rápidas */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-4">
            {/* Tabs de Opções */}
            <div className="flex items-center gap-1.5 p-1 bg-slate-800/70 rounded-2xl border border-slate-700/60 self-start">
              {meal.options.map((opt) => (
                <button
                  key={opt.optionNumber}
                  type="button"
                  onClick={() => setActiveOptionNumber(opt.optionNumber)}
                  className={`py-1.5 px-3.5 rounded-xl text-xs font-bold transition-all flex items-center gap-1.5 ${
                    activeOptionNumber === opt.optionNumber
                      ? 'bg-slate-900 text-white shadow-md border border-slate-700'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <span>Opção {opt.optionNumber}</span>
                  <span className="text-[10px] text-slate-400 font-normal">
                    ({opt.totalEnergyKcal.toFixed(0)} kcal)
                  </span>
                </button>
              ))}
            </div>

            {/* Botões de Ação da Refeição: "Na Dieta" e "Fora da Dieta" */}
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleToggleCheck}
                disabled={isPending}
                className={`flex items-center gap-1.5 py-2 px-3.5 rounded-2xl text-xs font-bold transition-all shadow-sm active:scale-95 border ${
                  isThisOptionCompleted
                    ? 'bg-emerald-500 text-slate-950 border-emerald-400 hover:bg-emerald-400'
                    : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20'
                }`}
                title="Marcar como consumida de acordo com o plano"
              >
                {isPending ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <CheckCircle2 className="w-3.5 h-3.5" />
                )}
                <span>
                  {isThisOptionCompleted
                    ? 'Na Dieta (Concluído)'
                    : 'Marcar Na Dieta'}
                </span>
              </button>

              <button
                type="button"
                onClick={() => onOpenOffDiet(meal.name)}
                className="flex items-center gap-1.5 py-2 px-3 rounded-2xl text-xs font-bold text-rose-400 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/20 transition-all active:scale-95"
                title="Lançar refeição livre / fora da dieta para este horário"
              >
                <UtensilsCrossed className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Fora da Dieta</span>
              </button>
            </div>
          </div>

          {/* Lista de Alimentos da Opção Selecionada */}
          <div className="border border-slate-800/80 rounded-2xl overflow-hidden bg-slate-950/40 divide-y divide-slate-800/50">
            {activeOption.items.map((item) => (
              <div
                key={item.id}
                className="p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-slate-900/40 transition-colors"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-white truncate">
                      {item.foodName}
                    </span>
                    <span className="text-[10px] font-bold text-emerald-400 bg-emerald-500/10 px-1.5 py-0.2 rounded border border-emerald-500/20">
                      {item.weightG.toFixed(0)}g
                    </span>
                  </div>

                  <div className="flex items-center gap-2 mt-1 text-[11px] text-slate-400">
                    <span className="font-semibold text-slate-300">
                      {item.energyKcal.toFixed(0)} kcal
                    </span>
                    <span>•</span>
                    <span className="text-cyan-400">P: {item.proteinG.toFixed(1)}g</span>
                    <span>•</span>
                    <span className="text-amber-400">C: {item.carbohydrateG.toFixed(1)}g</span>
                    <span>•</span>
                    <span className="text-rose-400">G: {item.lipidG.toFixed(1)}g</span>
                    {item.fiberG > 0 && (
                      <>
                        <span>•</span>
                        <span className="text-emerald-400">F: {item.fiberG.toFixed(1)}g</span>
                      </>
                    )}
                  </div>
                </div>

                {/* Botão para abrir o Modal de Substituição Equivalente */}
                <button
                  type="button"
                  onClick={() => onOpenSubstitution(meal.id, item, activeOption.optionNumber)}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800/70 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-700/60 text-xs font-semibold self-start sm:self-auto transition-all active:scale-95"
                  title="Substituir este alimento mantendo o macro dominante"
                >
                  <ArrowRightLeft className="w-3.5 h-3.5 text-emerald-400" />
                  <span>Substituir</span>
                </button>
              </div>
            ))}
          </div>

          {/* Totais Nutricionais da Opção */}
          <div className="bg-slate-800/30 border border-slate-800 rounded-2xl p-3 flex flex-wrap items-center justify-between gap-3 text-xs">
            <span className="font-bold text-slate-300 flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-amber-400" />
              Totais da Opção {activeOption.optionNumber}:
            </span>
            <div className="flex items-center gap-3 font-semibold text-slate-300">
              <span className="text-white font-bold">{activeOption.totalEnergyKcal.toFixed(0)} kcal</span>
              <span className="text-cyan-400">P: {activeOption.totalProteinG.toFixed(1)}g</span>
              <span className="text-amber-400">C: {activeOption.totalCarbohydrateG.toFixed(1)}g</span>
              <span className="text-rose-400">G: {activeOption.totalLipidG.toFixed(1)}g</span>
              <span className="text-emerald-400">F: {activeOption.totalFiberG.toFixed(1)}g</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export function MealCards({ meals, currentDate }: MealCardsProps) {
  // Estado para os Modais
  const [subModalData, setSubModalData] = useState<{
    isOpen: boolean;
    mealId: string;
    item: MealItemCalculated | null;
    optionNumber: number;
  }>({
    isOpen: false,
    mealId: '',
    item: null,
    optionNumber: 1,
  });

  const [offDietData, setOffDietData] = useState<{
    isOpen: boolean;
    mealName: string;
  }>({
    isOpen: false,
    mealName: '',
  });

  const handleOpenSubstitution = (mealId: string, item: MealItemCalculated, optionNumber: number) => {
    setSubModalData({
      isOpen: true,
      mealId,
      item,
      optionNumber,
    });
  };

  const handleOpenOffDiet = (mealName: string) => {
    setOffDietData({
      isOpen: true,
      mealName,
    });
  };

  const completedCount = meals.filter((m) => m.isCompleted).length;

  return (
    <div className="space-y-4">
      {/* Header da Seção de Refeições */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 px-1">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <Utensils className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight flex items-center gap-2">
              Gestor de Refeições Prescritas
              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-slate-800 text-emerald-400 border border-slate-700">
                {completedCount} de {meals.length} consumidas
              </span>
            </h2>
            <p className="text-xs text-slate-400">
              Alternância instantânea de opções, check de dieta e substituição equivalente em tempo real
            </p>
          </div>
        </div>
      </div>

      {/* Lista dos Cards de Refeição */}
      <div className="space-y-3.5">
        {meals.map((meal) => (
          <SingleMealCard
            key={meal.id}
            meal={meal}
            currentDate={currentDate}
            onOpenSubstitution={handleOpenSubstitution}
            onOpenOffDiet={handleOpenOffDiet}
          />
        ))}
      </div>

      {/* Modal de Substituição Equivalente */}
      {subModalData.item && (
        <SubstitutionModal
          isOpen={subModalData.isOpen}
          onClose={() => setSubModalData((prev) => ({ ...prev, isOpen: false }))}
          mealId={subModalData.mealId}
          item={subModalData.item}
          optionNumber={subModalData.optionNumber}
        />
      )}

      {/* Modal Fora da Dieta */}
      <OffDietModal
        isOpen={offDietData.isOpen}
        onClose={() => setOffDietData((prev) => ({ ...prev, isOpen: false }))}
        date={currentDate}
        mealName={offDietData.mealName}
      />
    </div>
  );
}
