'use client';

import React, { useEffect, useId, useRef, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Food } from '@/types/nutrition';
import { createCustomFoodServerAction } from '@/app/actions';
import { X, PlusCircle, Loader2, Sparkles, AlertCircle } from 'lucide-react';

interface CustomFoodModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated?: (food: Food) => void;
}

export function CustomFoodModal({ isOpen, onClose, onCreated }: CustomFoodModalProps) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [isPending, startTransition] = useTransition();

  const [name, setName] = useState('');
  const [category, setCategory] = useState('Personalizados');
  const [energyKcal, setEnergyKcal] = useState('');
  const [proteinG, setProteinG] = useState('');
  const [carbohydrateG, setCarbohydrateG] = useState('');
  const [lipidG, setLipidG] = useState('');
  const [fiberG, setFiberG] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const resetForm = () => {
    setName('');
    setCategory('Personalizados');
    setEnergyKcal('');
    setProteinG('');
    setCarbohydrateG('');
    setLipidG('');
    setFiberG('');
    setErrorMsg(null);
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
      resetForm();
    }
  }, [isOpen]);

  // Fallback de light-dismiss para navegadores sem suporte nativo a <dialog closedby="any"> (ex: Safari)
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

  const handleClose = () => {
    resetForm();
    onClose();
  };

  // Estimativa auxiliar de calorias via Atwater (4P + 4C + 9G)
  const parsedP = parseFloat(proteinG.replace(',', '.')) || 0;
  const parsedC = parseFloat(carbohydrateG.replace(',', '.')) || 0;
  const parsedG = parseFloat(lipidG.replace(',', '.')) || 0;
  const atwaterEstimate = parsedP * 4 + parsedC * 4 + parsedG * 9;

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setErrorMsg(null);

    const trimmedName = name.trim();
    if (trimmedName.length < 2) {
      setErrorMsg('Informe o nome do alimento (mínimo 2 caracteres).');
      return;
    }

    const kcal = parseFloat(energyKcal.replace(',', '.'));
    const protein = parseFloat(proteinG.replace(',', '.'));
    const carb = parseFloat(carbohydrateG.replace(',', '.'));
    const lipid = parseFloat(lipidG.replace(',', '.'));
    const fiber = fiberG.trim() === '' ? 0 : parseFloat(fiberG.replace(',', '.'));

    if (
      !Number.isFinite(kcal) || kcal < 0 ||
      !Number.isFinite(protein) || protein < 0 ||
      !Number.isFinite(carb) || carb < 0 ||
      !Number.isFinite(lipid) || lipid < 0 ||
      !Number.isFinite(fiber) || fiber < 0
    ) {
      setErrorMsg('Preencha todos os macros por 100g com valores numéricos maiores ou iguais a zero.');
      return;
    }

    startTransition(async () => {
      try {
        const created = await createCustomFoodServerAction({
          name: trimmedName,
          category: category.trim() || 'Personalizados',
          energyKcal: kcal,
          proteinG: protein,
          carbohydrateG: carb,
          lipidG: lipid,
          fiberG: fiber,
        });
        router.refresh();
        onCreated?.(created);
        handleClose();
      } catch (err: unknown) {
        setErrorMsg(
          err instanceof Error ? err.message : 'Erro ao cadastrar alimento customizado.'
        );
      }
    });
  };

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onClose={handleClose}
      {...({ closedby: 'any' } as Record<string, string>)}
      className="bg-slate-900 text-slate-100 border border-slate-700/80 rounded-3xl p-0 max-w-lg w-[92vw] shadow-2xl backdrop:bg-slate-950/80 backdrop:backdrop-blur-sm overflow-hidden"
    >
      {/* Header */}
      <div className="flex items-center justify-between p-5 border-b border-slate-800 bg-slate-900/95">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
            <PlusCircle className="w-5 h-5" />
          </div>
          <div>
            <h1 id={titleId} className="text-base font-black text-white tracking-tight">
              Novo Alimento Custom (Rótulo)
            </h1>
            <p className="text-xs text-slate-400">
              Informe os valores nutricionais por <strong className="text-slate-200">100g</strong>
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => dialogRef.current?.close()}
          aria-label="Fechar modal de novo alimento"
          className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="p-5 space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="sm:col-span-2 space-y-1.5">
            <label htmlFor="custom-food-name" className="block text-xs font-bold text-slate-300">
              Nome do Alimento *
            </label>
            <input
              id="custom-food-name"
              type="text"
              required
              autoFocus
              placeholder="Ex: Goma de Tapioca Da Terrinha, Whey Growth..."
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isPending}
              className="w-full px-3.5 py-2.5 bg-slate-950/70 border border-slate-700 rounded-xl text-xs font-medium text-white placeholder:text-slate-500 focus:border-emerald-400 focus:outline-none transition-colors"
            />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="custom-food-cat" className="block text-xs font-bold text-slate-300">
              Categoria
            </label>
            <input
              id="custom-food-cat"
              type="text"
              placeholder="Suplementos"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              disabled={isPending}
              className="w-full px-3 py-2.5 bg-slate-950/70 border border-slate-700 rounded-xl text-xs font-medium text-white placeholder:text-slate-500 focus:border-emerald-400 focus:outline-none transition-colors"
            />
          </div>
        </div>

        {/* Macros por 100g */}
        <div className="bg-slate-950/40 border border-slate-800 rounded-2xl p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
              Tabela Nutricional (por 100g)
            </span>
            {atwaterEstimate > 0 && (
              <button
                type="button"
                onClick={() => setEnergyKcal(atwaterEstimate.toFixed(0))}
                className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-400 hover:text-emerald-300 transition-colors"
                title="Preencher calorias automaticamente usando 4*P + 4*C + 9*G"
              >
                <Sparkles className="w-3 h-3" />
                <span>Estimar: {atwaterEstimate.toFixed(0)} kcal</span>
              </button>
            )}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <div className="space-y-1">
              <label htmlFor="custom-food-kcal" className="block text-[11px] font-bold text-slate-200">
                Energia (kcal) *
              </label>
              <input
                id="custom-food-kcal"
                type="number"
                inputMode="decimal"
                step="0.1"
                min="0"
                required
                placeholder="0"
                value={energyKcal}
                onChange={(e) => setEnergyKcal(e.target.value)}
                disabled={isPending}
                className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs font-bold text-white tabular-nums focus:border-emerald-400 focus:outline-none"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="custom-food-p" className="block text-[11px] font-bold text-cyan-400">
                Proteínas (g) *
              </label>
              <input
                id="custom-food-p"
                type="number"
                inputMode="decimal"
                step="0.1"
                min="0"
                required
                placeholder="0"
                value={proteinG}
                onChange={(e) => setProteinG(e.target.value)}
                disabled={isPending}
                className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs font-bold text-white tabular-nums focus:border-cyan-400 focus:outline-none"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="custom-food-c" className="block text-[11px] font-bold text-amber-400">
                Carboidratos (g) *
              </label>
              <input
                id="custom-food-c"
                type="number"
                inputMode="decimal"
                step="0.1"
                min="0"
                required
                placeholder="0"
                value={carbohydrateG}
                onChange={(e) => setCarbohydrateG(e.target.value)}
                disabled={isPending}
                className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs font-bold text-white tabular-nums focus:border-amber-400 focus:outline-none"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="custom-food-g" className="block text-[11px] font-bold text-rose-400">
                Gorduras (g) *
              </label>
              <input
                id="custom-food-g"
                type="number"
                inputMode="decimal"
                step="0.1"
                min="0"
                required
                placeholder="0"
                value={lipidG}
                onChange={(e) => setLipidG(e.target.value)}
                disabled={isPending}
                className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs font-bold text-white tabular-nums focus:border-rose-400 focus:outline-none"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="custom-food-f" className="block text-[11px] font-bold text-emerald-400">
                Fibras (g)
              </label>
              <input
                id="custom-food-f"
                type="number"
                inputMode="decimal"
                step="0.1"
                min="0"
                placeholder="0 (opcional)"
                value={fiberG}
                onChange={(e) => setFiberG(e.target.value)}
                disabled={isPending}
                className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs font-bold text-white tabular-nums focus:border-emerald-400 focus:outline-none"
              />
            </div>
          </div>
        </div>

        {errorMsg && (
          <div
            role="alert"
            className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300 flex items-center gap-2"
          >
            <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-800">
          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            disabled={isPending}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 transition-colors"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={isPending}
            className="inline-flex items-center gap-1.5 px-5 py-2 rounded-xl text-xs font-bold text-slate-950 bg-emerald-500 hover:bg-emerald-400 active:scale-95 transition-all disabled:opacity-50"
          >
            {isPending ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <PlusCircle className="w-3.5 h-3.5" />
            )}
            <span>{isPending ? 'Salvando...' : 'Salvar Alimento'}</span>
          </button>
        </div>
      </form>
    </dialog>
  );
}
