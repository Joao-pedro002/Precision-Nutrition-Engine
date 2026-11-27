'use client';

import React, { useState, useEffect, useTransition, useRef } from 'react';
import {
  MealItemCalculated,
  Food,
  SubstitutionResult,
  DominantMacro,
  QuickSubstitutionDto,
} from '@/types/nutrition';
import {
  searchFoodsServerAction,
  substituteFoodServerAction,
  applyItemSubstitutionServerAction,
  getQuickSubstitutionsServerAction,
} from '@/app/actions';
import {
  X,
  Search,
  Globe,
  ArrowRightLeft,
  AlertTriangle,
  CheckCircle2,
  Scale,
  Sparkles,
  Loader2,
  Zap,
  PlusCircle,
} from 'lucide-react';
import { CustomFoodModal } from '@/components/CustomFoodModal';

interface SubstitutionModalProps {
  isOpen: boolean;
  onClose: () => void;
  mealId: string;
  item: MealItemCalculated;
  optionNumber: number;
}

export function SubstitutionModal({
  isOpen,
  onClose,
  mealId,
  item,
  optionNumber,
}: SubstitutionModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [includeUsda, setIncludeUsda] = useState(true);
  const [searchResults, setSearchResults] = useState<Food[]>([]);
  const [isSearching, startSearchTransition] = useTransition();

  const [selectedFood, setSelectedFood] = useState<Food | null>(null);
  const [macroOverride, setMacroOverride] = useState<DominantMacro | 'KCAL' | null>(null);
  const [sourceWeight, setSourceWeight] = useState<string | number>(item.weightG);

  const [substitutionResult, setSubstitutionResult] = useState<SubstitutionResult | null>(null);
  const [isCalculating, startCalcTransition] = useTransition();
  const [isApplying, startApplyTransition] = useTransition();
  const [calcError, setCalcError] = useState<string | null>(null);

  const [quickSuggestions, setQuickSuggestions] = useState<QuickSubstitutionDto[]>([]);
  const [isLoadingQuick, setIsLoadingQuick] = useState<boolean>(false);
  const [isCustomFoodOpen, setIsCustomFoodOpen] = useState<boolean>(false);

  // Sincroniza o peso quando o item ou modal muda
  useEffect(() => {
    if (isOpen) {
      setSourceWeight(item.weightG);
    }
  }, [isOpen, item.weightG]);

  // Fetch silencioso de sugestões inteligentes de troca rápida ao abrir o modal
  useEffect(() => {
    if (!isOpen || !item.foodId) {
      setQuickSuggestions([]);
      return;
    }

    const numericWeight = typeof sourceWeight === 'number' ? sourceWeight : (Number(sourceWeight) || 0);
    if (numericWeight <= 0) return;

    let isMounted = true;
    setIsLoadingQuick(true);

    getQuickSubstitutionsServerAction(item.foodId, numericWeight, macroOverride)
      .then((data) => {
        if (isMounted) {
          setQuickSuggestions(data || []);
        }
      })
      .catch((err) => {
        console.warn('Sugestões rápidas indisponíveis:', err);
      })
      .finally(() => {
        if (isMounted) setIsLoadingQuick(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, item.foodId, sourceWeight, macroOverride]);

  // Controle do native <dialog>
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (isOpen) {
      if (!dialog.open) {
        dialog.showModal();
      }
    } else {
      if (dialog.open) {
        dialog.close();
      }
      // Reset state on close
      setSearchQuery('');
      setSearchResults([]);
      setSelectedFood(null);
      setSubstitutionResult(null);
      setCalcError(null);
    }
  }, [isOpen]);

  // Debounced search
  useEffect(() => {
    if (!isOpen) return;
    if (searchQuery.trim().length < 2) {
      setSearchResults([]);
      return;
    }

    const timer = setTimeout(() => {
      startSearchTransition(async () => {
        try {
          const results = await searchFoodsServerAction(searchQuery.trim(), includeUsda);
          setSearchResults(results.filter((f) => f.id !== item.foodId));
        } catch (err) {
          console.error('Erro na busca de alimentos:', err);
        }
      });
    }, 500);

    return () => clearTimeout(timer);
  }, [searchQuery, includeUsda, isOpen, item.foodId]);

  // Calculate substitution whenever selectedFood or macroOverride changes
  useEffect(() => {
    if (!selectedFood) {
      setSubstitutionResult(null);
      setCalcError(null);
      return;
    }

    const numericWeight = typeof sourceWeight === 'number' ? sourceWeight : (Number(sourceWeight) || 0);
    if (numericWeight <= 0) {
      setSubstitutionResult(null);
      return;
    }

    startCalcTransition(async () => {
      setCalcError(null);
      try {
        const result = await substituteFoodServerAction({
          sourceFoodId: item.foodId,
          sourceWeightG: numericWeight,
          targetFoodId: selectedFood.id,
          targetMacroOverride: macroOverride,
        });
        setSubstitutionResult(result);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Substituição incompatível';
        setCalcError(msg);
        setSubstitutionResult(null);
      }
    });
  }, [selectedFood, macroOverride, sourceWeight, item.foodId]);

  const handleApply = () => {
    if (!substitutionResult || !selectedFood) return;

    startApplyTransition(async () => {
      try {
        await applyItemSubstitutionServerAction(
          mealId,
          item.id,
          selectedFood.id,
          substitutionResult.targetFood.weightG,
          optionNumber
        );
        onClose();
      } catch (err) {
        console.error('Erro ao aplicar substituição:', err);
      }
    });
  };

  return (
    <>
    <dialog
      ref={dialogRef}
      onClose={onClose}
      aria-labelledby="modal-sub-title"
      className="backdrop:bg-slate-950/80 backdrop:backdrop-blur-sm bg-slate-900 border border-slate-800 text-slate-100 rounded-3xl p-0 w-full max-w-2xl shadow-2xl overflow-hidden overscroll-contain"
    >
      {/* Header */}
      <div className="flex items-center justify-between p-5 border-b border-slate-800 bg-slate-900/90">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <ArrowRightLeft className="w-4 h-4" />
          </div>
          <div>
            <h2 id="modal-sub-title" className="text-base font-bold text-white tracking-tight">
              Calculadora de Substituição Equivalente
            </h2>
            <p className="text-xs text-slate-400">
              Preservação estrita do macro dominante com auditoria de Calorie Drift
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={onClose}
          className="p-1.5 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          title="Fechar"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="p-6 space-y-6 max-h-[80vh] overflow-y-auto">
        {/* Alimento de Origem (Atual) */}
        <div className="bg-slate-800/40 border border-slate-700/60 rounded-2xl p-4">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Alimento Atual na Refeição
          </span>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mt-1.5">
            <div>
              <h3 className="text-sm font-bold text-white">{item.foodName}</h3>
              <div className="flex items-center gap-2 mt-1 text-xs text-slate-300">
                <span className="font-semibold text-emerald-400">
                  {item.weightG.toFixed(0)}g
                </span>
                <span>•</span>
                <span>{item.energyKcal.toFixed(0)} kcal</span>
                <span>•</span>
                <span>P: {item.proteinG.toFixed(1)}g</span>
                <span>•</span>
                <span>C: {item.carbohydrateG.toFixed(1)}g</span>
                <span>•</span>
                <span>G: {item.lipidG.toFixed(1)}g</span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-400">Peso:</span>
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                value={typeof sourceWeight === 'string' ? sourceWeight : String(sourceWeight)}
                onChange={(e) => {
                  const val = e.target.value.trim();
                  if (val === '') {
                    setSourceWeight('');
                  } else {
                    const cleaned = val.replace(/^0+(?=\d)/, '');
                    if (/^\d*$/.test(cleaned)) {
                      setSourceWeight(cleaned);
                    }
                  }
                }}
                className="w-20 px-2.5 py-1 bg-slate-900 border border-slate-700 rounded-xl text-xs font-bold text-white text-center focus:border-emerald-500 focus:outline-none"
              />
              <span className="text-xs text-slate-400">g</span>
            </div>
          </div>
        </div>

        {/* Sugestões Inteligentes (Troca Rápida Top 5 com menor Calorie Drift) */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center gap-1.5">
              <Zap className="w-3.5 h-3.5 text-amber-400" />
              Sugestões Inteligentes (Troca Rápida)
            </span>
            {isLoadingQuick && (
              <span className="text-[10px] text-slate-400 flex items-center gap-1">
                <Loader2 className="w-3 h-3 animate-spin text-emerald-400" />
                Carregando Top 5...
              </span>
            )}
          </div>

          {quickSuggestions.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              {quickSuggestions.map((sub) => {
                const isSelected = selectedFood?.id === sub.targetFoodId;
                const absDelta = Math.abs(sub.deltaKcal);
                const isRed = sub.calorieDriftWarning || Math.abs(sub.calorieDriftPercent) > 10;
                const isYellow = !isRed && (absDelta > 15 || Math.abs(sub.calorieDriftPercent) > 5);

                const badgeStyle = isRed
                  ? 'bg-rose-500/15 text-rose-300 border-rose-500/30'
                  : isYellow
                  ? 'bg-amber-500/15 text-amber-300 border-amber-500/30'
                  : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30';

                return (
                  <button
                    key={sub.targetFoodId}
                    type="button"
                    onClick={() => {
                      setSelectedFood({
                        id: sub.targetFoodId,
                        name: sub.targetFoodName,
                        source: 'TACO',
                        externalId: null,
                        category: sub.foodGroup,
                        dominantMacro: 'PROTEIN',
                        servingSizeG: 100,
                        energyKcal: sub.energyKcal,
                        proteinG: sub.proteinG,
                        carbohydrateG: sub.carbohydrateG,
                        lipidG: sub.lipidG,
                        fiberG: 0,
                        micronutrients: {
                          calcium_mg: 0,
                          iron_mg: 0,
                          magnesium_mg: 0,
                          potassium_mg: 0,
                          sodium_mg: 0,
                          zinc_mg: 0,
                          vitamin_c_mg: 0,
                          vitamin_b12_mcg: 0,
                          cholesterol_mg: 0,
                        },
                      });
                    }}
                    className={`p-3 rounded-2xl border text-left transition-all relative ${
                      isSelected
                        ? 'bg-emerald-500/15 border-emerald-500/60 shadow-lg ring-1 ring-emerald-500/40'
                        : 'bg-slate-800/40 border-slate-700/60 hover:bg-slate-800 hover:border-slate-600'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="text-xs font-bold text-white line-clamp-1">
                        {sub.targetFoodName}
                      </span>
                      <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full border shrink-0 ${badgeStyle}`}>
                        {sub.deltaKcal > 0 ? `+${sub.deltaKcal.toFixed(0)}` : sub.deltaKcal.toFixed(0)} kcal
                      </span>
                    </div>

                    <div className="flex items-center gap-2 mt-2 text-[11px] text-slate-400">
                      <span className="font-bold text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded border border-emerald-500/20">
                        {sub.calculatedWeightG.toFixed(0)}g
                      </span>
                      <span>P: {sub.proteinG.toFixed(0)}g</span>
                      <span>•</span>
                      <span>C: {sub.carbohydrateG.toFixed(0)}g</span>
                      <span>•</span>
                      <span>G: {sub.lipidG.toFixed(0)}g</span>
                    </div>

                    {sub.portions && sub.portions.length > 0 && (
                      <div className="mt-1.5 text-[10px] text-slate-400 truncate">
                        Porção sugerida: <span className="text-slate-300 font-medium">{sub.portions[0].portionName}</span>
                      </div>
                    )}
                  </button>
                );
              })}
            </div>
          ) : !isLoadingQuick ? (
            <p className="text-xs text-slate-500 italic bg-slate-800/20 p-2.5 rounded-xl border border-slate-800">
              Nenhuma alternativa direta mapeada no grupo culinário atual. Use a busca abaixo.
            </p>
          ) : null}
        </div>

        {/* Barra de Busca de Alimentos */}
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <label className="block text-xs font-bold uppercase tracking-wider text-slate-300">
              Buscar Alimento Substituto (TACO, USDA ou Custom)
            </label>
            <button
              type="button"
              onClick={() => setIsCustomFoodOpen(true)}
              className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-400 hover:text-emerald-300 bg-emerald-500/10 hover:bg-emerald-500/20 px-2.5 py-1 rounded-lg border border-emerald-500/25 transition-colors whitespace-nowrap"
            >
              <PlusCircle className="w-3.5 h-3.5" aria-hidden="true" />
              <span>Novo Alimento</span>
            </button>
          </div>

          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="w-4 h-4 absolute left-3.5 top-3 text-slate-400 pointer-events-none" />
              <input
                type="search"
                placeholder="Ex: patinho, ovo, batata doce, banana..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-10 pr-4 py-2.5 bg-slate-800/80 border border-slate-700 rounded-2xl text-xs font-medium text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none transition-colors"
                autoFocus
              />
            </div>

            <button
              type="button"
              onClick={() => setIncludeUsda(!includeUsda)}
              className={`flex items-center gap-1.5 px-3 py-2.5 rounded-2xl text-xs font-bold border transition-all ${
                includeUsda
                  ? 'bg-sky-500/20 text-sky-300 border-sky-500/40 shadow-sm'
                  : 'bg-slate-800/60 text-slate-400 border-slate-700 hover:text-white'
              }`}
              title="Buscar na base global USDA FoodData Central com tradução e cache automático"
            >
              <Globe className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Base USDA</span>
            </button>
          </div>

          {/* Lista de Resultados da Busca */}
          {isSearching && (
            <div className="flex items-center justify-center py-4 text-xs text-slate-400 gap-2">
              <Loader2 className="w-4 h-4 animate-spin text-emerald-400" />
              <span>Consultando índice trigramático...</span>
            </div>
          )}

          {!isSearching && searchResults.length > 0 && !selectedFood && (
            <div className="max-h-48 overflow-y-auto border border-slate-800 rounded-2xl bg-slate-900/90 divide-y divide-slate-800/60 shadow-inner">
              {searchResults.map((food) => (
                <button
                  key={food.id}
                  type="button"
                  onClick={() => setSelectedFood(food)}
                  className="w-full text-left p-3 hover:bg-slate-800/60 transition-colors flex items-center justify-between gap-3 group"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-white group-hover:text-emerald-400 truncate">
                        {food.name}
                      </span>
                      <span
                        className={`text-[9px] font-black px-1.5 py-0.2 rounded border ${
                          food.source === 'USDA'
                            ? 'bg-sky-500/10 text-sky-400 border-sky-500/20'
                            : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                        }`}
                      >
                        {food.source}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      100g: {food.energyKcal.toFixed(0)} kcal • P: {food.proteinG.toFixed(1)}g • C: {food.carbohydrateG.toFixed(1)}g • G: {food.lipidG.toFixed(1)}g
                    </p>
                  </div>
                  <span className="text-[11px] font-semibold text-emerald-400 opacity-0 group-hover:opacity-100 transition-opacity">
                    Selecionar &rarr;
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Alimento Selecionado e Configuração da Regra */}
        {selectedFood && (
          <div className="space-y-4 pt-2 border-t border-slate-800">
            <div className="flex items-center justify-between bg-slate-800/60 border border-slate-700 rounded-2xl p-3.5">
              <div>
                <span className="text-[10px] font-bold text-slate-400 uppercase">Substituto Escolhido:</span>
                <h3 className="text-sm font-bold text-emerald-400 mt-0.5">{selectedFood.name}</h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedFood(null)}
                className="text-xs font-semibold text-slate-400 hover:text-white"
              >
                Trocar Alimento
              </button>
            </div>

            {/* Seletor do Macro a Igualar (Regra Híbrida) */}
            <div className="space-y-1.5">
              <span className="text-xs font-bold text-slate-300">
                Critério de Equivalência (Macro-Alvo):
              </span>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {[
                  { id: null, label: 'Automático (Regra Híbrida)' },
                  { id: 'PROTEIN' as const, label: 'Proteína' },
                  { id: 'CARB' as const, label: 'Carboidrato' },
                  { id: 'KCAL' as const, label: 'Isocalórico (Kcal)' },
                ].map((opt) => (
                  <button
                    key={opt.label}
                    type="button"
                    onClick={() => setMacroOverride(opt.id)}
                    className={`py-2 px-2.5 rounded-xl text-xs font-bold border transition-all text-center ${
                      macroOverride === opt.id
                        ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300 shadow-sm'
                        : 'bg-slate-800/40 border-slate-700/60 text-slate-400 hover:text-white'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Estado de Cálculo e Erro de Incompatibilidade */}
            {isCalculating && (
              <div className="flex items-center justify-center py-4 text-xs text-slate-400 gap-2">
                <Loader2 className="w-4 h-4 animate-spin text-emerald-400" />
                <span>Calculando equivalência matemática...</span>
              </div>
            )}

            {calcError && (
              <div className="bg-rose-500/10 border border-rose-500/20 rounded-2xl p-4 flex items-start gap-3 text-rose-300">
                <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
                <p className="text-xs font-medium">{calcError}</p>
              </div>
            )}

            {/* Resultado da Substituição e Alerta de Calorie Drift */}
            {substitutionResult && (
              <div className="space-y-4 pt-2">
                {/* Comparação Lado a Lado */}
                <div className="grid grid-cols-2 gap-3">
                  <div className="bg-slate-800/30 border border-slate-800 rounded-2xl p-3 text-center">
                    <span className="text-[10px] font-bold text-slate-400 uppercase">Original</span>
                    <p className="text-base font-black text-white mt-1">
                      {substitutionResult.sourceFood.weightG.toFixed(0)}g
                    </p>
                    <p className="text-xs font-semibold text-slate-300 mt-0.5">
                      {substitutionResult.sourceFood.energyKcal.toFixed(0)} kcal
                    </p>
                    <p className="text-[11px] text-slate-400 mt-1">
                      P: {substitutionResult.sourceFood.proteinG.toFixed(1)}g • C: {substitutionResult.sourceFood.carbohydrateG.toFixed(1)}g • G: {substitutionResult.sourceFood.lipidG.toFixed(1)}g
                    </p>
                  </div>

                  <div className="bg-emerald-500/5 border border-emerald-500/30 rounded-2xl p-3 text-center">
                    <span className="text-[10px] font-bold text-emerald-400 uppercase flex items-center justify-center gap-1">
                      <Scale className="w-3 h-3" /> Peso Equivalente
                    </span>
                    <p className="text-base font-black text-emerald-400 mt-1">
                      {substitutionResult.targetFood.weightG.toFixed(0)}g
                    </p>
                    <p className="text-xs font-semibold text-slate-200 mt-0.5">
                      {substitutionResult.targetFood.energyKcal.toFixed(0)} kcal
                    </p>
                    <p className="text-[11px] text-slate-400 mt-1">
                      P: {substitutionResult.targetFood.proteinG.toFixed(1)}g • C: {substitutionResult.targetFood.carbohydrateG.toFixed(1)}g • G: {substitutionResult.targetFood.lipidG.toFixed(1)}g
                    </p>
                  </div>
                </div>

                {/* ALERTA DE CALORIE DRIFT (|Δkcal| > 10%) */}
                {substitutionResult.calorieDriftWarning ? (
                  <div className="bg-amber-500/10 border border-amber-500/30 rounded-2xl p-4 space-y-2">
                    <div className="flex items-center gap-2 text-amber-400 font-bold text-xs">
                      <AlertTriangle className="w-4 h-4 shrink-0" />
                      <span>Alerta de Variação Calórica (Calorie Drift &gt; 10%)</span>
                    </div>
                    <p className="text-xs text-amber-200/90 leading-relaxed">
                      {substitutionResult.warningMessage}
                    </p>
                    <div className="flex items-center gap-4 text-xs font-bold pt-1">
                      <span className={substitutionResult.deltaKcal >= 0 ? 'text-rose-400' : 'text-emerald-400'}>
                        Delta: {substitutionResult.deltaKcal >= 0 ? `+${substitutionResult.deltaKcal.toFixed(0)}` : substitutionResult.deltaKcal.toFixed(0)} kcal ({substitutionResult.calorieDriftPercent.toFixed(1)}%)
                      </span>
                      <span className="text-slate-400">
                        ΔG: {substitutionResult.deltaLipidG >= 0 ? `+${substitutionResult.deltaLipidG.toFixed(1)}` : substitutionResult.deltaLipidG.toFixed(1)}g gordura
                      </span>
                    </div>
                  </div>
                ) : (
                  <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-2xl p-3 flex items-center gap-2.5 text-xs text-emerald-300">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                    <span>Substituição isocalórica/compatível dentro da tolerância de &plusmn;10% calórica.</span>
                  </div>
                )}

                {/* Botão de Confirmação */}
                <button
                  type="button"
                  onClick={handleApply}
                  disabled={isApplying}
                  className="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 active:scale-98 text-slate-950 font-black text-sm shadow-lg shadow-emerald-500/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  {isApplying ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Sparkles className="w-4 h-4" />
                  )}
                  <span>Confirmar Substituição no Plano ({substitutionResult.targetFood.weightG.toFixed(0)}g)</span>
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </dialog>

    <CustomFoodModal
      isOpen={isCustomFoodOpen}
      onClose={() => setIsCustomFoodOpen(false)}
      onCreated={(createdFood) => {
        setSelectedFood(createdFood);
        setSearchQuery(createdFood.name);
      }}
    />
    </>
  );
}
