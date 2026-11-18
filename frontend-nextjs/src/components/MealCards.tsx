'use client';

import React, { useState, useEffect, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { MealDashboard, MealItemCalculated, DailyLogEntry } from '@/types/nutrition';
import { checkMealServerAction, uncheckMealServerAction } from '@/app/actions';
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
  Trash2,
} from 'lucide-react';

interface MealCardsProps {
  meals: MealDashboard[];
  currentDate: string;
  loggedEntries?: DailyLogEntry[];
}

interface SingleMealCardProps {
  meal: MealDashboard;
  isCompleted: boolean;
  onToggleCheck: (mealId: string, optionNumber: number) => void;
  isPendingCheck: boolean;
  onOpenSubstitution: (mealId: string, item: MealItemCalculated, optionNumber: number) => void;
  onOpenOffDiet: (mealId: string, mealName: string) => void;
  onRemoveOffDiet: (mealId: string) => void;
  offDietEntry?: { customName: string; energyKcal: number } | null;
}

function getHouseholdPortionLabel(foodName: string, weightG: number): string | null {
  if (!foodName || !weightG || weightG <= 0) return null;
  const lower = foodName.toLowerCase();

  if (lower.includes('clara')) {
    const qty = Math.round(weightG / 30);
    return qty > 0 ? `${qty} ${qty === 1 ? 'unidade' : 'unidades'}` : null;
  }
  if (lower.includes('ovo')) {
    const qty = Math.round(weightG / 50);
    return qty > 0 ? `${qty} ${qty === 1 ? 'unidade' : 'unidades'}` : null;
  }
  if (lower.includes('pão, francês') || lower.includes('pao frances')) {
    const qty = Math.round(weightG / 50);
    return qty > 0 ? `${qty} ${qty === 1 ? 'unidade' : 'unidades'}` : null;
  }
  if (lower.includes('pão') || lower.includes('pao')) {
    const qty = Math.round(weightG / 25);
    return qty > 0 ? `${qty} ${qty === 1 ? 'fatia' : 'fatias'}` : null;
  }
  if (lower.includes('leite')) {
    const qty = Math.round((weightG / 200) * 10) / 10;
    return qty === 1 ? '1 copo (200ml)' : `${qty} copos`;
  }
  if (lower.includes('arroz')) {
    const qty = Math.round(weightG / 25);
    return qty > 0 ? `${qty} ${qty === 1 ? 'colher de sopa' : 'colheres de sopa'}` : null;
  }
  if (lower.includes('feijão') || lower.includes('feijao')) {
    const qty = Math.round((weightG / 130) * 10) / 10;
    return qty === 1 ? '1 concha' : `${qty} conchas`;
  }
  if (lower.includes('banana')) {
    const qty = Math.round(weightG / 100);
    return qty > 0 ? `${qty} ${qty === 1 ? 'unidade' : 'unidades'}` : null;
  }
  if (lower.includes('azeite')) {
    const qty = Math.round(weightG / 13);
    return qty > 0 ? `${qty} ${qty === 1 ? 'colher de sopa' : 'colheres de sopa'}` : null;
  }
  if (lower.includes('aveia')) {
    const qty = Math.round(weightG / 15);
    return qty > 0 ? `${qty} ${qty === 1 ? 'colher de sopa' : 'colheres de sopa'}` : null;
  }
  if (lower.includes('frango') || lower.includes('carne') || lower.includes('patinho')) {
    const qty = Math.round((weightG / 100) * 10) / 10;
    return qty === 1 ? '1 filé médio' : `${qty} filés`;
  }
  return null;
}

function SingleMealCard({
  meal,
  isCompleted,
  onToggleCheck,
  isPendingCheck,
  onOpenSubstitution,
  onOpenOffDiet,
  onRemoveOffDiet,
  offDietEntry,
}: SingleMealCardProps) {
  const [isExpanded, setIsExpanded] = useState<boolean>(true);
  const [activeOptionNumber, setActiveOptionNumber] = useState<number>(
    meal.completedOptionNumber || 1
  );

  const handleToggleCheck = () => {
    onToggleCheck(meal.id, activeOptionNumber);
  };

  // Encontra a opção ativa selecionada no card
  const activeOption = meal.options.find((o) => o.optionNumber === activeOptionNumber) || meal.options[0];

  return (
    <div
      className={`rounded-3xl border transition-all duration-300 overflow-hidden shadow-lg ${
        isCompleted
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
              isCompleted
                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                : 'bg-slate-800 text-slate-400 border border-slate-700'
            }`}
          >
            {isCompleted ? (
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
              {isCompleted && (
                <span className="text-[10px] font-black uppercase px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shrink-0">
                  Opção {activeOptionNumber} Realizada
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
                {offDietEntry ? `${offDietEntry.energyKcal.toFixed(0)} kcal` : `${activeOption?.totalEnergyKcal.toFixed(0)} kcal`}
              </span>
              {!offDietEntry && (
                <>
                  <span>•</span>
                  <span>P: {activeOption?.totalProteinG.toFixed(1)}g</span>
                </>
              )}
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
            {/* Tabs de Opções (ocultadas quando em refeição livre fora da dieta) */}
            {!offDietEntry ? (
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
            ) : (
              <span className="text-xs font-bold text-rose-400 uppercase tracking-wider flex items-center gap-1.5">
                <UtensilsCrossed className="w-4 h-4" />
                Refeição Livre Ativa
              </span>
            )}

            {/* Botões de Ação da Refeição: "Na Dieta" e "Fora da Dieta" */}
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleToggleCheck}
                disabled={isPendingCheck || Boolean(offDietEntry)}
                className={`flex items-center gap-1.5 py-2 px-3.5 rounded-2xl text-xs font-bold transition-all shadow-sm border ${
                  Boolean(offDietEntry)
                    ? 'opacity-50 cursor-not-allowed bg-slate-800 text-slate-500 border-slate-700'
                    : isCompleted
                    ? 'bg-emerald-500 text-slate-950 border-emerald-400 hover:bg-emerald-400 active:scale-95'
                    : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20 active:scale-95'
                }`}
                title={
                  Boolean(offDietEntry)
                    ? 'Desfaça a refeição livre antes de marcar na dieta'
                    : 'Marcar como consumida de acordo com o plano'
                }
              >
                {isPendingCheck ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <CheckCircle2 className="w-3.5 h-3.5" />
                )}
                <span>
                  {isCompleted
                    ? 'Na Dieta (Concluído)'
                    : 'Marcar Na Dieta'}
                </span>
              </button>

              {offDietEntry ? (
                <button
                  type="button"
                  onClick={() => onRemoveOffDiet(meal.id)}
                  disabled={isPendingCheck}
                  className="flex items-center gap-1.5 py-2 px-3 rounded-2xl text-xs font-bold text-rose-300 bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 transition-all active:scale-95 shadow-sm"
                  title="Desfazer e remover lançamento de refeição livre"
                >
                  <Trash2 className="w-3.5 h-3.5 text-rose-400" />
                  <span>Desfazer Refeição Livre</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => onOpenOffDiet(meal.id, meal.name)}
                  className="flex items-center gap-1.5 py-2 px-3 rounded-2xl text-xs font-bold text-rose-400 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/20 transition-all active:scale-95"
                  title="Lançar refeição livre / fora da dieta para este horário"
                >
                  <UtensilsCrossed className="w-3.5 h-3.5" />
                  <span className="hidden sm:inline">Fora da Dieta</span>
                </button>
              )}
            </div>
          </div>

          {/* Se a refeição tiver lançamento fora da dieta registrado, oculta alimentos originais */}
          {offDietEntry ? (
            <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-rose-500/20 text-rose-400">
                  <UtensilsCrossed className="w-4 h-4" />
                </div>
                <div>
                  <span className="text-[10px] uppercase font-bold text-rose-400 tracking-wider block">
                    Refeição Livre Registrada (Fora da Dieta)
                  </span>
                  <span className="text-sm font-bold text-white">
                    1x {offDietEntry.customName}
                  </span>
                </div>
              </div>
              <span className="text-base font-extrabold text-rose-300 font-mono">
                {offDietEntry.energyKcal.toFixed(0)} kcal
              </span>
            </div>
          ) : (
            <>
              {/* Lista de Alimentos da Opção Selecionada */}
              <div className="border border-slate-800/80 rounded-2xl overflow-hidden bg-slate-950/40 divide-y divide-slate-800/50">
                {activeOption.items.map((item) => {
                  const portionLabel = getHouseholdPortionLabel(item.foodName, item.weightG);

                  return (
                    <div
                      key={item.id}
                      className="p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-slate-900/40 transition-colors"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-xs font-bold text-white truncate">
                            {item.foodName}
                          </span>
                          {portionLabel ? (
                            <>
                              <span className="text-[11px] font-bold text-emerald-400 bg-emerald-500/15 px-2 py-0.5 rounded-lg border border-emerald-500/30">
                                {portionLabel}
                              </span>
                              <span className="text-[10px] text-gray-500 font-mono">
                                ({item.weightG.toFixed(0)}g)
                              </span>
                            </>
                          ) : (
                            <span className="text-[10px] font-mono text-gray-400 bg-slate-800/80 px-1.5 py-0.5 rounded border border-slate-700/60">
                              {item.weightG.toFixed(0)}g
                            </span>
                          )}
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
                  );
                })}
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
            </>
          )}
        </div>
      )}
    </div>
  );
}

export function MealCards({ meals, currentDate, loggedEntries }: MealCardsProps) {
  const router = useRouter();

  // 1. Dicionário de conclusão de refeições (Corrige o Radio Button Bug)
  const [completedMeals, setCompletedMeals] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    meals.forEach((m) => {
      initial[m.id] = m.isCompleted;
    });
    return initial;
  });

  const [isPendingCheck, startCheckTransition] = useTransition();

  // Sincroniza o dicionário quando a lista de meals muda
  useEffect(() => {
    const next: Record<string, boolean> = {};
    meals.forEach((m) => {
      next[m.id] = m.isCompleted;
    });
    setCompletedMeals(next);
  }, [meals]);

  // Lógica de Toggle Imutável por ID específico
  const handleToggleCheck = (mealId: string, optionNumber: number) => {
    setCompletedMeals((prev) => ({
      ...prev,
      [mealId]: !prev[mealId],
    }));

    startCheckTransition(async () => {
      try {
        await checkMealServerAction(currentDate, mealId, optionNumber);
        router.refresh();
      } catch (err) {
        console.error('Erro ao atualizar checagem de refeição:', err);
      }
    });
  };

  // 2. Binding do Modal Fora da Dieta (OffDietModal) e estado de refeições livres ativas
  const [offDietMealId, setOffDietMealId] = useState<string | null>(null);
  const [offDietMealName, setOffDietMealName] = useState<string>('');

  const [offDietEntries, setOffDietEntries] = useState<Record<string, { customName: string; energyKcal: number }>>(() => {
    const initial: Record<string, { customName: string; energyKcal: number }> = {};
    if (loggedEntries) {
      loggedEntries
        .filter((e) => e.isOffDiet && e.mealId)
        .forEach((e) => {
          if (e.mealId) {
            initial[e.mealId] = {
              customName: e.foodNameSnapshot || 'Refeição Livre',
              energyKcal: e.consumedEnergyKcal || 0,
            };
          }
        });
    }
    return initial;
  });

  // Sincroniza com as entradas salvas do backend quando a página revalida
  useEffect(() => {
    const next: Record<string, { customName: string; energyKcal: number }> = {};
    if (loggedEntries) {
      loggedEntries
        .filter((e) => e.isOffDiet && e.mealId)
        .forEach((e) => {
          if (e.mealId) {
            next[e.mealId] = {
              customName: e.foodNameSnapshot || 'Refeição Livre',
              energyKcal: e.consumedEnergyKcal || 0,
            };
          }
        });
    }
    setOffDietEntries(next);
  }, [loggedEntries]);

  const handleOpenOffDiet = (mealId: string, mealName: string) => {
    setOffDietMealId(mealId);
    setOffDietMealName(mealName);
  };

  const handleOffDietSuccess = (entry: { mealId: string; customName: string; energyKcal: number }) => {
    setOffDietEntries((prev) => ({
      ...prev,
      [entry.mealId]: {
        customName: entry.customName,
        energyKcal: entry.energyKcal,
      },
    }));
    router.refresh();
  };

  // 3. Desfazer Refeição Livre (Remoção da refeição livre com Server Action)
  const handleRemoveOffDiet = (mealId: string) => {
    setOffDietEntries((prev) => {
      const copy = { ...prev };
      delete copy[mealId];
      return copy;
    });

    startCheckTransition(async () => {
      try {
        await uncheckMealServerAction(currentDate, mealId);
        router.refresh();
      } catch (err) {
        console.error('Erro ao remover refeição livre:', err);
      }
    });
  };

  // 4. Modal de Substituição Equivalente
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

  const handleOpenSubstitution = (mealId: string, item: MealItemCalculated, optionNumber: number) => {
    setSubModalData({
      isOpen: true,
      mealId,
      item,
      optionNumber,
    });
  };

  const completedCount = meals.filter((m) => Boolean(completedMeals[m.id])).length;

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

      {/* Lista dos Cards de Refeição: key={meal.id} estrita */}
      <div className="space-y-3.5">
        {meals.map((meal) => (
          <SingleMealCard
            key={meal.id}
            meal={meal}
            isCompleted={Boolean(completedMeals[meal.id])}
            onToggleCheck={handleToggleCheck}
            isPendingCheck={isPendingCheck}
            onOpenSubstitution={handleOpenSubstitution}
            onOpenOffDiet={handleOpenOffDiet}
            onRemoveOffDiet={handleRemoveOffDiet}
            offDietEntry={offDietEntries[meal.id]}
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
        isOpen={offDietMealId !== null}
        onClose={() => setOffDietMealId(null)}
        date={currentDate}
        mealId={offDietMealId}
        mealName={offDietMealName}
        onSuccess={handleOffDietSuccess}
      />
    </div>
  );
}
