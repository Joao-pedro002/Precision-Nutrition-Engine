'use client';

import React, { useState, useEffect, useRef, useTransition } from 'react';
import { addOffDietServerAction } from '@/app/actions';
import { X, UtensilsCrossed, Sparkles, Loader2 } from 'lucide-react';

interface OffDietModalProps {
  isOpen: boolean;
  onClose: () => void;
  date: string;
  mealId?: string | null;
  mealName?: string;
  onSuccess?: (entry: { mealId: string; customName: string; energyKcal: number }) => void;
}

export function OffDietModal({ isOpen, onClose, date, mealId, mealName, onSuccess }: OffDietModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [isPending, startTransition] = useTransition();

  const [name, setName] = useState('');
  const [weightG, setWeightG] = useState('100');
  const [energyKcal, setEnergyKcal] = useState('250');
  const [proteinG, setProteinG] = useState('10');
  const [carbG, setCarbG] = useState('30');
  const [lipidG, setLipidG] = useState('10');
  const [fiberG, setFiberG] = useState('2');

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
      setName('');
    }
  }, [isOpen]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const parsedWeight = Number(weightG) || 100;
    const parsedKcal = Number(energyKcal) || 0;
    const parsedProtein = Number(proteinG) || 0;
    const parsedCarb = Number(carbG) || 0;
    const parsedLipid = Number(lipidG) || 0;
    const parsedFiber = Number(fiberG) || 0;

    startTransition(async () => {
      try {
        await addOffDietServerAction(date, {
          mealId: mealId || null,
          foodId: null,
          customName: name.trim(),
          weightConsumedG: parsedWeight,
          energyKcal: parsedKcal,
          proteinG: parsedProtein,
          carbohydrateG: parsedCarb,
          lipidG: parsedLipid,
          fiberG: parsedFiber,
        });

        if (mealId && onSuccess) {
          onSuccess({
            mealId,
            customName: name.trim(),
            energyKcal: parsedKcal,
          });
        }
        onClose();
      } catch (err) {
        console.error('Erro ao adicionar refeição livre:', err);
      }
    });
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      aria-labelledby="modal-offdiet-title"
      className="backdrop:bg-slate-950/80 backdrop:backdrop-blur-sm bg-slate-900 border border-slate-800 text-slate-100 rounded-3xl p-0 w-full max-w-lg shadow-2xl overflow-hidden overscroll-contain"
    >
      <div className="flex items-center justify-between p-5 border-b border-slate-800 bg-slate-900/90">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
            <UtensilsCrossed className="w-4 h-4" />
          </div>
          <div>
            <h2 id="modal-offdiet-title" className="text-base font-bold text-white tracking-tight">
              Lançamento Fora da Dieta {mealName ? `(${mealName})` : ''}
            </h2>
            <p className="text-xs text-slate-400">
              Registra uma refeição livre sem alterar os templates da dieta
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

      <form onSubmit={handleSubmit} className="p-6 space-y-4">
        <div>
          <label className="block text-xs font-bold uppercase tracking-wider text-slate-300 mb-1.5">
            Descrição do Alimento / Refeição Livre
          </label>
          <input
            type="text"
            required
            placeholder="Ex: 2 fatias de pizza de calabresa, sorvete, bolo..."
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-4 py-2.5 bg-slate-800/80 border border-slate-700 rounded-2xl text-xs font-medium text-white placeholder-slate-500 focus:border-rose-500 focus:outline-none transition-colors"
            autoFocus
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">
              Peso Consumido (g)
            </label>
            <input
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              value={weightG}
              onChange={(e) => setWeightG(e.target.value.replace(/^0+(?=\d)/, ''))}
              className="w-full px-3 py-2 bg-slate-800/80 border border-slate-700 rounded-xl text-xs text-white focus:border-rose-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">
              Calorias Totais (kcal)
            </label>
            <input
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              value={energyKcal}
              onChange={(e) => setEnergyKcal(e.target.value.replace(/^0+(?=\d)/, ''))}
              className="w-full px-3 py-2 bg-slate-800/80 border border-slate-700 rounded-xl text-xs font-bold text-rose-300 focus:border-rose-500 focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-4 gap-2 pt-1">
          <div>
            <label className="block text-[11px] font-semibold text-cyan-400 mb-1">
              Proteína (g)
            </label>
            <input
              type="text"
              inputMode="decimal"
              value={proteinG}
              onChange={(e) => setProteinG(e.target.value.replace(/^0+(?=\d)/, ''))}
              className="w-full px-2 py-1.5 bg-slate-800/80 border border-slate-700 rounded-xl text-xs text-white text-center focus:border-cyan-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-[11px] font-semibold text-amber-400 mb-1">
              Carboidrato (g)
            </label>
            <input
              type="text"
              inputMode="decimal"
              value={carbG}
              onChange={(e) => setCarbG(e.target.value.replace(/^0+(?=\d)/, ''))}
              className="w-full px-2 py-1.5 bg-slate-800/80 border border-slate-700 rounded-xl text-xs text-white text-center focus:border-amber-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-[11px] font-semibold text-rose-400 mb-1">
              Gordura (g)
            </label>
            <input
              type="text"
              inputMode="decimal"
              value={lipidG}
              onChange={(e) => setLipidG(e.target.value.replace(/^0+(?=\d)/, ''))}
              className="w-full px-2 py-1.5 bg-slate-800/80 border border-slate-700 rounded-xl text-xs text-white text-center focus:border-rose-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-[11px] font-semibold text-emerald-400 mb-1">
              Fibras (g)
            </label>
            <input
              type="text"
              inputMode="decimal"
              value={fiberG}
              onChange={(e) => setFiberG(e.target.value.replace(/^0+(?=\d)/, ''))}
              className="w-full px-2 py-1.5 bg-slate-800/80 border border-slate-700 rounded-xl text-xs text-white text-center focus:border-emerald-500 focus:outline-none"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={isPending || !name.trim()}
          className="w-full mt-4 py-3 px-4 rounded-2xl bg-gradient-to-r from-rose-500 to-amber-500 hover:from-rose-400 hover:to-amber-400 active:scale-98 text-white font-black text-xs shadow-lg shadow-rose-500/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
        >
          {isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
          <span>Registrar Lançamento Fora da Dieta</span>
        </button>
      </form>
    </dialog>
  );
}
