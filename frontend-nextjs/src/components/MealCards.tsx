'use client';

import React, { useState, useEffect, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { MealDashboard, MealItemCalculated, DailyLogEntry } from '@/types/nutrition';
import {
  checkMealServerAction,
  uncheckMealServerAction,
  updateMealItemWeightServerAction,
  removeMealItemServerAction,
  deleteMealServerAction,
} from '@/app/actions';
import { SubstitutionModal } from '@/components/SubstitutionModal';
import { OffDietModal } from '@/components/OffDietModal';
import { CustomFoodModal } from '@/components/CustomFoodModal';
import { AddMealItemModal } from '@/components/AddMealItemModal';
import { CreateMealModal } from '@/components/CreateMealModal';
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
  Pencil,
  Check,
  X,
  Plus,
  PlusCircle,
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
  onUpdateItemWeight: (itemId: string, weightG: number) => Promise<void>;
  onRemoveMealItem: (itemId: string) => Promise<void>;
  onOpenAddItem: (mealId: string, mealName: string, optionNumber: number) => void;
  onDeleteMeal: (mealId: string) => Promise<void>;
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
  onUpdateItemWeight,
  onRemoveMealItem,
  onOpenAddItem,
  onDeleteMeal,
  offDietEntry,
}: SingleMealCardProps) {
  const [isExpanded, setIsExpanded] = useState<boolean>(true);
  const [activeOptionNumber, setActiveOptionNumber] = useState<number>(
    meal.completedOptionNumber || 1
  );
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [editingWeight, setEditingWeight] = useState<string>('');
  const [confirmDeleteItemId, setConfirmDeleteItemId] = useState<string | null>(null);
  const [confirmDeleteMeal, setConfirmDeleteMeal] = useState<boolean>(false);
  const [isMutatingItem, setIsMutatingItem] = useState<boolean>(false);

  const handleStartEditWeight = (item: MealItemCalculated) => {
    setConfirmDeleteItemId(null);
    setEditingItemId(item.id);
    setEditingWeight(String(Math.round(item.weightG * 10) / 10));
  };

  const handleConfirmEditWeight = async (itemId: string) => {
    const parsed = parseFloat(editingWeight.replace(',', '.'));
    if (!Number.isFinite(parsed) || parsed <= 0) return;
    setIsMutatingItem(true);
    try {
      await onUpdateItemWeight(itemId, parsed);
      setEditingItemId(null);
    } finally {
      setIsMutatingItem(false);
    }
  };

  const handleConfirmRemoveItem = async (itemId: string) => {
    setIsMutatingItem(true);
    try {
      await onRemoveMealItem(itemId);
      setConfirmDeleteItemId(null);
    } finally {
      setIsMutatingItem(false);
    }
  };

  const handleConfirmDeleteMeal = async () => {
    setIsMutatingItem(true);
    try {
      await onDeleteMeal(meal.id);
      setConfirmDeleteMeal(false);
    } finally {
      setIsMutatingItem(false);
    }
  };

  const handleToggleCheck = () => {
    onToggleCheck(meal.id, activeOptionNumber);
  };

  // Encontra a opção ativa selecionada no card (fallback para Opção 1 vazia)
  const activeOption = meal.options.find((o) => o.optionNumber === activeOptionNumber) || meal.options[0] || {
    optionNumber: 1,
    totalEnergyKcal: 0,
    totalProteinG: 0,
    totalCarbohydrateG: 0,
    totalLipidG: 0,
    totalFiberG: 0,
    items: [],
  };

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

        <div
          className="flex items-center gap-2 shrink-0"
          onClick={(e) => e.stopPropagation()}
        >
          {confirmDeleteMeal ? (
            <div className="flex items-center gap-1.5 bg-rose-950/50 border border-rose-500/30 rounded-xl px-2.5 py-1 text-[11px]">
              <span className="text-rose-200 font-semibold">Excluir?</span>
              <button
                type="button"
                onClick={handleConfirmDeleteMeal}
                disabled={isMutatingItem}
                className="px-2 py-0.5 rounded-lg bg-rose-500 hover:bg-rose-400 text-slate-950 font-bold transition-colors disabled:opacity-50"
              >
                {isMutatingItem ? '...' : 'Sim'}
              </button>
              <button
                type="button"
                onClick={() => setConfirmDeleteMeal(false)}
                disabled={isMutatingItem}
                className="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold transition-colors"
              >
                Não
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setConfirmDeleteMeal(true)}
              className="p-1.5 rounded-xl text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
              title="Excluir refeição do plano"
              aria-label={`Excluir refeição ${meal.name}`}
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}

          <button
            type="button"
            onClick={() => setIsExpanded(!isExpanded)}
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
                disabled={isPendingCheck || Boolean(offDietEntry) || activeOption.items.length === 0}
                className={`flex items-center gap-1.5 py-2 px-3.5 rounded-2xl text-xs font-bold transition-all shadow-sm border ${
                  Boolean(offDietEntry) || activeOption.items.length === 0
                    ? 'opacity-50 cursor-not-allowed bg-slate-800 text-slate-500 border-slate-700'
                    : isCompleted
                    ? 'bg-emerald-500 text-slate-950 border-emerald-400 hover:bg-emerald-400 active:scale-95'
                    : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20 active:scale-95'
                }`}
                title={
                  Boolean(offDietEntry)
                    ? 'Desfaça a refeição livre antes de marcar na dieta'
                    : activeOption.items.length === 0
                    ? 'Adicione ao menos um alimento antes de marcar como concluída'
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
              {activeOption.items.length === 0 ? (
                <div className="border border-slate-800/80 rounded-2xl p-4 bg-slate-950/40 text-center text-xs text-slate-400">
                  Nenhum alimento adicionado na Opção {activeOption.optionNumber} ainda. Clique abaixo para montar esta refeição.
                </div>
              ) : (
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

                      {/* Ações do Item: Editar Gramagem, Substituir e Remover */}
                      <div className="flex items-center gap-1.5 self-start sm:self-auto shrink-0 flex-wrap">
                        {editingItemId === item.id ? (
                          <div className="flex items-center gap-1 bg-slate-900 border border-emerald-500/40 rounded-xl p-1">
                            <input
                              type="number"
                              inputMode="decimal"
                              step="1"
                              min="1"
                              aria-label={`Nova gramagem de ${item.foodName}`}
                              value={editingWeight}
                              onChange={(e) => setEditingWeight(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') {
                                  e.preventDefault();
                                  handleConfirmEditWeight(item.id);
                                } else if (e.key === 'Escape') {
                                  setEditingItemId(null);
                                }
                              }}
                              disabled={isMutatingItem}
                              className="w-16 px-2 py-1 bg-slate-950 border border-slate-700 rounded-lg text-xs font-bold text-white text-center tabular-nums focus:border-emerald-400 focus:outline-none"
                              autoFocus
                            />
                            <span className="text-[10px] text-slate-400 font-semibold pr-0.5">g</span>
                            <button
                              type="button"
                              onClick={() => handleConfirmEditWeight(item.id)}
                              disabled={isMutatingItem}
                              aria-label="Confirmar nova gramagem"
                              className="p-1 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 transition-colors disabled:opacity-50"
                            >
                              {isMutatingItem ? (
                                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                              ) : (
                                <Check className="w-3.5 h-3.5" />
                              )}
                            </button>
                            <button
                              type="button"
                              onClick={() => setEditingItemId(null)}
                              disabled={isMutatingItem}
                              aria-label="Cancelar edição de gramagem"
                              className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                            >
                              <X className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        ) : confirmDeleteItemId === item.id ? (
                          <div className="flex items-center gap-1.5 bg-rose-950/40 border border-rose-500/30 rounded-xl px-2 py-1 text-[11px]">
                            <span className="text-rose-200 font-semibold">Remover?</span>
                            <button
                              type="button"
                              onClick={() => handleConfirmRemoveItem(item.id)}
                              disabled={isMutatingItem}
                              className="px-2 py-0.5 rounded-lg bg-rose-500 hover:bg-rose-400 text-slate-950 font-bold transition-colors disabled:opacity-50"
                            >
                              {isMutatingItem ? '...' : 'Sim'}
                            </button>
                            <button
                              type="button"
                              onClick={() => setConfirmDeleteItemId(null)}
                              disabled={isMutatingItem}
                              className="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold transition-colors"
                            >
                              Não
                            </button>
                          </div>
                        ) : (
                          <>
                            <button
                              type="button"
                              onClick={() => handleStartEditWeight(item)}
                              className="p-1.5 rounded-xl bg-slate-800/60 hover:bg-slate-800 text-slate-400 hover:text-emerald-300 border border-slate-700/50 transition-all active:scale-95"
                              title="Editar gramagem (g)"
                              aria-label={`Editar gramagem de ${item.foodName}`}
                            >
                              <Pencil className="w-3.5 h-3.5" />
                            </button>

                            <button
                              type="button"
                              onClick={() => onOpenSubstitution(meal.id, item, activeOption.optionNumber)}
                              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800/70 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-700/60 text-xs font-semibold transition-all active:scale-95"
                              title="Substituir este alimento mantendo o macro dominante"
                            >
                              <ArrowRightLeft className="w-3.5 h-3.5 text-emerald-400" />
                              <span>Substituir</span>
                            </button>

                            <button
                              type="button"
                              onClick={() => {
                                setEditingItemId(null);
                                setConfirmDeleteItemId(item.id);
                              }}
                              className="p-1.5 rounded-xl bg-slate-800/60 hover:bg-rose-500/15 text-slate-400 hover:text-rose-400 border border-slate-700/50 hover:border-rose-500/30 transition-all active:scale-95"
                              title="Remover alimento desta opção"
                              aria-label={`Remover ${item.foodName}`}
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
              )}

              {/* Botão Discreto para Adicionar Alimento à Opção Ativa */}
              <button
                type="button"
                onClick={() => onOpenAddItem(meal.id, meal.name, activeOption.optionNumber)}
                className="w-full py-2 px-3 rounded-2xl border border-dashed border-slate-700/80 hover:border-emerald-500/40 bg-slate-900/30 hover:bg-emerald-500/5 text-xs font-semibold text-slate-400 hover:text-emerald-300 flex items-center justify-center gap-1.5 transition-all"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Adicionar Alimento à Opção {activeOption.optionNumber}</span>
              </button>

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

  // 5. Edição de Gramagem, Remoção, Adição de Item, Criação/Exclusão de Refeição e Cadastro de Alimento Custom
  const [isCustomFoodModalOpen, setIsCustomFoodModalOpen] = useState<boolean>(false);
  const [isCreateMealModalOpen, setIsCreateMealModalOpen] = useState<boolean>(false);
  const [addItemModalData, setAddItemModalData] = useState<{
    isOpen: boolean;
    mealId: string;
    mealName: string;
    optionNumber: number;
  }>({
    isOpen: false,
    mealId: '',
    mealName: '',
    optionNumber: 1,
  });

  const handleUpdateItemWeight = async (itemId: string, weightG: number) => {
    await updateMealItemWeightServerAction(itemId, weightG);
    router.refresh();
  };

  const handleRemoveMealItem = async (itemId: string) => {
    await removeMealItemServerAction(itemId);
    router.refresh();
  };

  const handleDeleteMeal = async (mealId: string) => {
    await deleteMealServerAction(mealId);
    router.refresh();
  };

  const handleOpenAddItem = (mealId: string, mealName: string, optionNumber: number) => {
    setAddItemModalData({
      isOpen: true,
      mealId,
      mealName,
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
            <h2 className="text-base font-bold text-white tracking-tight flex items-center gap-2 flex-wrap">
              Gestor de Refeições Prescritas
              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-slate-800 text-emerald-400 border border-slate-700">
                {completedCount} de {meals.length} consumidas
              </span>
            </h2>
            <p className="text-xs text-slate-400">
              Alternância de opções, edição de gramagens, cadastro custom e substituição em tempo real
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto flex-wrap">
          <button
            type="button"
            onClick={() => setIsCreateMealModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-2xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 border border-emerald-400 text-xs font-black transition-all active:scale-95 whitespace-nowrap shadow-sm"
          >
            <Plus className="w-4 h-4" aria-hidden="true" />
            <span>Nova Refeição</span>
          </button>

          <button
            type="button"
            onClick={() => setIsCustomFoodModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-2xl bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-xs font-bold transition-all active:scale-95 whitespace-nowrap"
          >
            <PlusCircle className="w-4 h-4 text-emerald-400" aria-hidden="true" />
            <span>Novo Alimento</span>
          </button>
        </div>
      </div>

      {/* Empty State Acionável quando não há refeições no plano (ex: Produção com banco limpo) */}
      {meals.length === 0 ? (
        <div className="bg-slate-900/60 border border-dashed border-slate-700/80 rounded-3xl p-8 sm:p-10 text-center space-y-4 shadow-lg">
          <div className="flex justify-center">
            <div className="w-14 h-14 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
              <Utensils className="w-6 h-6" />
            </div>
          </div>

          <div className="max-w-md mx-auto space-y-1.5">
            <h3 className="text-base font-extrabold text-white tracking-tight">
              Seu plano ainda não possui refeições
            </h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Crie sua primeira refeição (ex: Café da Manhã, Almoço ou Jantar) para começar a montar o cardápio com alimentos da tabela TACO, USDA ou cadastrados por rótulo.
            </p>
          </div>

          <div className="pt-1">
            <button
              type="button"
              onClick={() => setIsCreateMealModalOpen(true)}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-2xl bg-emerald-500 hover:bg-emerald-400 active:scale-95 text-slate-950 font-black text-xs transition-all shadow-lg shadow-emerald-500/20"
            >
              <Plus className="w-4 h-4" aria-hidden="true" />
              <span>Criar Primeira Refeição</span>
            </button>
          </div>
        </div>
      ) : (
        /* Lista dos Cards de Refeição: key={meal.id} estrita */
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
              onUpdateItemWeight={handleUpdateItemWeight}
              onRemoveMealItem={handleRemoveMealItem}
              onOpenAddItem={handleOpenAddItem}
              onDeleteMeal={handleDeleteMeal}
              offDietEntry={offDietEntries[meal.id]}
            />
          ))}
        </div>
      )}

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

      {/* Modal de Nova Refeição */}
      <CreateMealModal
        isOpen={isCreateMealModalOpen}
        onClose={() => setIsCreateMealModalOpen(false)}
      />

      {/* Modal de Cadastro de Alimento Custom */}
      <CustomFoodModal
        isOpen={isCustomFoodModalOpen}
        onClose={() => setIsCustomFoodModalOpen(false)}
      />

      {/* Modal de Adicionar Alimento à Opção da Refeição */}
      <AddMealItemModal
        isOpen={addItemModalData.isOpen}
        onClose={() => setAddItemModalData((prev) => ({ ...prev, isOpen: false }))}
        mealId={addItemModalData.mealId}
        mealName={addItemModalData.mealName}
        optionNumber={addItemModalData.optionNumber}
      />
    </div>
  );
}
