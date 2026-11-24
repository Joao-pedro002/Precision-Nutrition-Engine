'use client';

import React, { useEffect, useId, useRef, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { createMealServerAction } from '@/app/actions';
import { X, Utensils, Plus, Loader2, AlertCircle, Clock } from 'lucide-react';

interface CreateMealModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const MEAL_PRESETS = [
  { name: 'Café da Manhã', time: '07:30' },
  { name: 'Almoço', time: '12:30' },
  { name: 'Lanche da Tarde', time: '16:00' },
  { name: 'Jantar', time: '20:00' },
  { name: 'Ceia', time: '22:30' },
];

export function CreateMealModal({ isOpen, onClose }: CreateMealModalProps) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [isPending, startTransition] = useTransition();

  const [name, setName] = useState('');
  const [targetTime, setTargetTime] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const resetForm = () => {
    setName('');
    setTargetTime('');
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

  // Fallback de light-dismiss para navegadores sem suporte nativo a <dialog closedby="any">
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

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setErrorMsg(null);

    const trimmedName = name.trim();
    if (trimmedName.length < 2) {
      setErrorMsg('Informe o nome da refeição (ex: Café da Manhã, Almoço).');
      return;
    }

    startTransition(async () => {
      try {
        await createMealServerAction(trimmedName, targetTime || null);
        router.refresh();
        handleClose();
      } catch (err: unknown) {
        setErrorMsg(
          err instanceof Error ? err.message : 'Erro ao criar refeição no plano.'
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
      className="bg-slate-900 text-slate-100 border border-slate-700/80 rounded-3xl p-0 max-w-md w-[92vw] shadow-2xl backdrop:bg-slate-950/80 backdrop:backdrop-blur-sm overflow-hidden"
    >
      {/* Header */}
      <div className="flex items-center justify-between p-5 border-b border-slate-800 bg-slate-900/95">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
            <Utensils className="w-5 h-5" />
          </div>
          <div>
            <h1 id={titleId} className="text-base font-black text-white tracking-tight">
              Nova Refeição no Plano
            </h1>
            <p className="text-xs text-slate-400">
              Crie um horário para adicionar seus alimentos
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => dialogRef.current?.close()}
          aria-label="Fechar modal de nova refeição"
          className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="p-5 space-y-4">
        {/* Sugestões rápidas */}
        <div className="space-y-1.5">
          <span className="block text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Atalhos Rápidos
          </span>
          <div className="flex flex-wrap gap-1.5">
            {MEAL_PRESETS.map((preset) => (
              <button
                key={preset.name}
                type="button"
                onClick={() => {
                  setName(preset.name);
                  setTargetTime(preset.time);
                }}
                className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-slate-800/80 hover:bg-emerald-500/15 text-slate-300 hover:text-emerald-300 border border-slate-700/70 hover:border-emerald-500/30 transition-colors"
              >
                {preset.name}
              </button>
            ))}
          </div>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="create-meal-name" className="block text-xs font-bold text-slate-300">
            Nome da Refeição *
          </label>
          <input
            id="create-meal-name"
            type="text"
            required
            autoFocus
            maxLength={100}
            placeholder="Ex: Café da Manhã, Almoço, Pré-Treino..."
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={isPending}
            className="w-full px-3.5 py-2.5 bg-slate-950/70 border border-slate-700 rounded-xl text-xs font-medium text-white placeholder:text-slate-500 focus:border-emerald-400 focus:outline-none transition-colors"
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="create-meal-time" className="block text-xs font-bold text-slate-300 flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5 text-slate-400" />
            <span>Horário Previsto (opcional)</span>
          </label>
          <input
            id="create-meal-time"
            type="time"
            value={targetTime}
            onChange={(e) => setTargetTime(e.target.value)}
            disabled={isPending}
            className="w-full px-3.5 py-2.5 bg-slate-950/70 border border-slate-700 rounded-xl text-xs font-bold text-white tabular-nums focus:border-emerald-400 focus:outline-none transition-colors"
          />
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
            disabled={isPending || !name.trim()}
            className="inline-flex items-center gap-1.5 px-5 py-2 rounded-xl text-xs font-bold text-slate-950 bg-emerald-500 hover:bg-emerald-400 active:scale-95 transition-all disabled:opacity-40"
          >
            {isPending ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Plus className="w-3.5 h-3.5" />
            )}
            <span>{isPending ? 'Criando...' : 'Criar Refeição'}</span>
          </button>
        </div>
      </form>
    </dialog>
  );
}
