'use client';

import React, { useEffect, useId, useRef, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Food } from '@/types/nutrition';
import {
  addMealOptionItemServerAction,
  searchFoodsServerAction,
} from '@/app/actions';
import { CustomFoodModal } from '@/components/CustomFoodModal';
import {
  X,
  Search,
  Globe,
  Plus,
  Loader2,
  Check,
  AlertCircle,
  PlusCircle,
} from 'lucide-react';

interface AddMealItemModalProps {
  isOpen: boolean;
  onClose: () => void;
  mealId: string;
  mealName: string;
  optionNumber: number;
}

export function AddMealItemModal({
  isOpen,
  onClose,
  mealId,
  mealName,
  optionNumber,
}: AddMealItemModalProps) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  const [searchQuery, setSearchQuery] = useState('');
  const [includeUsda, setIncludeUsda] = useState(false);
  const [searchResults, setSearchResults] = useState<Food[]>([]);
  const [selectedFood, setSelectedFood] = useState<Food | null>(null);
  const [weightG, setWeightG] = useState('100');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isCustomModalOpen, setIsCustomModalOpen] = useState(false);

  const [isSearching, startSearchTransition] = useTransition();
  const [isSaving, startSaveTransition] = useTransition();

  const resetState = () => {
    setSearchQuery('');
    setSearchResults([]);
    setSelectedFood(null);
    setWeightG('100');
    setErrorMsg(null);
    setIsCustomModalOpen(false);
  };

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (isOpen) {
      if (!dialog.open) {
        dialog.showModal();
      }
    } else if (dialog.open) {
      dialog.close();
      resetState();
    }
  }, [isOpen]);

  // Fallback de light-dismiss quando <dialog closedby="any"> não existe
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog || typeof window === 'undefined') return;
    if ('closedBy' in HTMLDialogElement.prototype) return;

    const isOutside = (e: PointerEvent) => {
      const r = dialog.getBoundingClientRect();
      return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
    };

    let pressedOutside = false;
    const onPointerDown = (e: PointerEvent) => {
      pressedOutside = isOutside(e);
    };
    const onPointerUp = (e: PointerEvent) => {
      if (pressedOutside && isOutside(e)) {
        dialog.close();
      }
    };

    dialog.addEventListener('pointerdown', onPointerDown);
    dialog.addEventListener('pointerup', onPointerUp);
    return () => {
      dialog.removeEventListener('pointerdown', onPointerDown);
      dialog.removeEventListener('pointerup', onPointerUp);
    };
  }, []);

  // Busca com debounce (reaproveita searchFoodsServerAction)
  useEffect(() => {
    if (!isOpen) return;
    const trimmed = searchQuery.trim();
    if (trimmed.length < 2) {
      setSearchResults([]);
      return;
    }

    const timer = setTimeout(() => {
      startSearchTransition(async () => {
        try {
          const results = await searchFoodsServerAction(trimmed, includeUsda);
          setSearchResults(results);
        } catch (err) {
          console.error('Erro na busca de alimentos:', err);
        }
      });
    }, 450);

    return () => clearTimeout(timer);
  }, [searchQuery, includeUsda, isOpen]);

  const handleClose = () => {
    resetState();
    onClose();
  };

  const parsedWeight = parseFloat(weightG.replace(',', '.')) || 0;
  const factor = selectedFood && selectedFood.servingSizeG > 0 ? parsedWeight / selectedFood.servingSizeG : parsedWeight / 100;

  const handleAdd = () => {
    if (!selectedFood) return;
    setErrorMsg(null);

    if (!Number.isFinite(parsedWeight) || parsedWeight <= 0) {
      setErrorMsg('Informe uma gramagem válida maior que zero.');
      return;
    }

    startSaveTransition(async () => {
      try {
        await addMealOptionItemServerAction(mealId, optionNumber, selectedFood.id, parsedWeight);
        router.refresh();
        handleClose();
      } catch (err: unknown) {
        setErrorMsg(
          err instanceof Error ? err.message : 'Erro ao adicionar item à refeição.'
        );
      }
    });
  };

  return (
    <>
      <dialog
        ref={dialogRef}
        aria-labelledby={titleId}
        onClose={handleClose}
        {...({ closedby: 'any' } as Record<string, string>)}
        className="bg-slate-900 text-slate-100 border border-slate-700/80 rounded-3xl p-0 max-w-xl w-[92vw] shadow-2xl backdrop:bg-slate-950/80 backdrop:backdrop-blur-sm overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-800 bg-slate-900/95">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
              <Plus className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h1 id={titleId} className="text-base font-black text-white tracking-tight truncate">
                Adicionar Alimento — {mealName}
              </h1>
              <p className="text-xs text-slate-400">
                Opção {optionNumber} • TACO, USDA ou Custom
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            aria-label="Fechar modal de adicionar alimento"
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-5 space-y-4">
          <div className="space-y-2.5">
            <div className="flex items-center justify-between gap-2">
              <label htmlFor="add-item-search" className="text-xs font-bold uppercase tracking-wider text-slate-300">
                Buscar Alimento
              </label>
              <button
                type="button"
                onClick={() => setIsCustomModalOpen(true)}
                className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-400 hover:text-emerald-300 bg-emerald-500/10 hover:bg-emerald-500/20 px-2.5 py-1 rounded-lg border border-emerald-500/25 transition-colors"
              >
                <PlusCircle className="w-3.5 h-3.5" aria-hidden="true" />
                <span>Novo Alimento</span>
              </button>
            </div>

            <div className="flex gap-2">
              <div className="relative flex-1">
                <Search className="w-4 h-4 absolute left-3.5 top-3 text-slate-400 pointer-events-none" />
                <input
                  id="add-item-search"
                  type="search"
                  autoFocus
                  placeholder="Ex: tapioca, whey, arroz, frango..."
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                    if (selectedFood) setSelectedFood(null);
                  }}
                  className="w-full pl-10 pr-4 py-2.5 bg-slate-950/70 border border-slate-700 rounded-2xl text-xs font-medium text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none transition-colors"
                />
              </div>

              <button
                type="button"
                onClick={() => setIncludeUsda(!includeUsda)}
                className={`flex items-center gap-1.5 px-3 py-2.5 rounded-2xl text-xs font-bold border transition-all whitespace-nowrap ${
                  includeUsda
                    ? 'bg-sky-500/20 text-sky-300 border-sky-500/40'
                    : 'bg-slate-800/60 text-slate-400 border-slate-700 hover:text-white'
                }`}
              >
                <Globe className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">USDA</span>
              </button>
            </div>

            {isSearching && (
              <div className="flex items-center justify-center py-4 text-xs text-slate-400 gap-2">
                <Loader2 className="w-4 h-4 animate-spin text-emerald-400" />
                <span>Buscando alimentos...</span>
              </div>
            )}

            {!isSearching && searchResults.length > 0 && !selectedFood && (
              <div className="max-h-52 overflow-y-auto border border-slate-800 rounded-2xl bg-slate-950/70 divide-y divide-slate-800/60">
                {searchResults.map((food) => (
                  <button
                    key={food.id}
                    type="button"
                    onClick={() => setSelectedFood(food)}
                    className="w-full text-left p-3 hover:bg-slate-800/60 transition-colors flex items-center justify-between gap-3"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-white truncate">{food.name}</span>
                        <span
                          className={`text-[9px] font-bold uppercase px-1.5 py-0.5 rounded border shrink-0 ${
                            food.source === 'CUSTOM'
                              ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                              : food.source === 'USDA'
                              ? 'bg-sky-500/15 text-sky-300 border-sky-500/30'
                              : 'bg-slate-800 text-slate-400 border-slate-700'
                          }`}
                        >
                          {food.source}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 mt-1 text-[11px] text-slate-400 tabular-nums">
                        <span>{food.energyKcal.toFixed(0)} kcal / 100g</span>
                        <span>•</span>
                        <span className="text-cyan-400">P: {food.proteinG.toFixed(1)}g</span>
                        <span className="text-amber-400">C: {food.carbohydrateG.toFixed(1)}g</span>
                        <span className="text-rose-400">G: {food.lipidG.toFixed(1)}g</span>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Alimento selecionado + Gramagem */}
          {selectedFood && (
            <div className="bg-emerald-950/20 border border-emerald-500/30 rounded-2xl p-4 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <span className="text-[10px] uppercase font-bold tracking-wider text-emerald-400">
                    Alimento Selecionado ({selectedFood.source})
                  </span>
                  <h2 className="text-sm font-bold text-white mt-0.5">{selectedFood.name}</h2>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedFood(null)}
                  className="text-xs text-slate-400 hover:text-white underline"
                >
                  Trocar
                </button>
              </div>

              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1">
                <div className="flex items-center gap-2">
                  <label htmlFor="add-item-weight" className="text-xs font-bold text-slate-300">
                    Quantidade:
                  </label>
                  <input
                    id="add-item-weight"
                    type="number"
                    inputMode="decimal"
                    step="1"
                    min="1"
                    value={weightG}
                    onChange={(e) => setWeightG(e.target.value)}
                    disabled={isSaving}
                    className="w-24 px-3 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-xs font-bold text-white text-center tabular-nums focus:border-emerald-400 focus:outline-none"
                  />
                  <span className="text-xs font-semibold text-slate-400">g</span>
                </div>

                <div className="flex items-center gap-2.5 text-xs tabular-nums flex-wrap">
                  <span className="font-bold text-white">
                    {(selectedFood.energyKcal * factor).toFixed(0)} kcal
                  </span>
                  <span className="text-cyan-400">P: {(selectedFood.proteinG * factor).toFixed(1)}g</span>
                  <span className="text-amber-400">C: {(selectedFood.carbohydrateG * factor).toFixed(1)}g</span>
                  <span className="text-rose-400">G: {(selectedFood.lipidG * factor).toFixed(1)}g</span>
                </div>
              </div>
            </div>
          )}

          {errorMsg && (
            <div
              role="alert"
              className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300 flex items-center gap-2"
            >
              <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-800">
            <button
              type="button"
              onClick={() => dialogRef.current?.close()}
              disabled={isSaving}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 transition-colors"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={handleAdd}
              disabled={isSaving || !selectedFood}
              className="inline-flex items-center gap-1.5 px-5 py-2 rounded-xl text-xs font-bold text-slate-950 bg-emerald-500 hover:bg-emerald-400 active:scale-95 transition-all disabled:opacity-40 disabled:pointer-events-none"
            >
              {isSaving ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Check className="w-3.5 h-3.5" />
              )}
              <span>{isSaving ? 'Adicionando...' : `Adicionar à Opção ${optionNumber}`}</span>
            </button>
          </div>
        </div>
      </dialog>

      <CustomFoodModal
        isOpen={isCustomModalOpen}
        onClose={() => setIsCustomModalOpen(false)}
        onCreated={(createdFood) => {
          setSelectedFood(createdFood);
          setSearchQuery(createdFood.name);
        }}
      />
    </>
  );
}
