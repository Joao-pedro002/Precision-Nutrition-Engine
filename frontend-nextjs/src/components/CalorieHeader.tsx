'use client';

import React, { useState, useEffect } from 'react';
import { DailySummaryDto, DailyTarget } from '@/types/nutrition';
import { Flame, ShieldAlert, Sparkles, TrendingUp, LogOut, Loader2 } from 'lucide-react';
import { TargetWizardModal } from '@/components/TargetWizardModal';
import { deactivateActiveTargetServerAction } from '@/app/actions';

interface CalorieHeaderProps {
  summary: DailySummaryDto;
  activeTarget: DailyTarget | null;
  currentDate: string;
  mealsCount?: number;
}

export function CalorieHeader({ summary, activeTarget, currentDate, mealsCount = 0 }: CalorieHeaderProps) {
  const hasActiveTarget = Boolean(activeTarget && activeTarget.id);
  const [isWizardOpen, setIsWizardOpen] = useState(!hasActiveTarget);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  // Auto-Onboarding: Se não há protocolo ativo, força abertura do Wizard
  useEffect(() => {
    if (!hasActiveTarget) {
      setIsWizardOpen(true);
    }
  }, [hasActiveTarget]);

  const handleResetTarget = async () => {
    if (confirm('Deseja desativar a meta atual e reconfigurar seu protocolo nutricional?')) {
      setIsLoggingOut(true);
      try {
        await deactivateActiveTargetServerAction();
        window.location.reload();
      } catch (err) {
        console.error('Erro ao desativar meta:', err);
        window.location.reload();
      }
    }
  };

  const {
    consumedEnergyKcal,
    targetKcalMin,
    targetKcalMax,
    maintenanceKcalCeiling,
    caloricZone
  } = summary;

  // Percentual relativo ao teto de manutenção para a barra de progresso (com clamp entre 0 e 100%)
  const maxScale = maintenanceKcalCeiling > 0 ? maintenanceKcalCeiling * 1.15 : 2800;
  const progressPercent = Math.min(100, Math.max(0, (consumedEnergyKcal / maxScale) * 100));

  const minZonePercent = (targetKcalMin / maxScale) * 100;
  const maxZonePercent = (targetKcalMax / maxScale) * 100;
  const ceilingZonePercent = (maintenanceKcalCeiling / maxScale) * 100;

  // Informações visuais da zona calórica atual
  const getZoneBadge = () => {
    switch (caloricZone.zone) {
      case 'OPTIMAL_DEFICIT':
        return {
          label: 'Déficit Ótimo',
          icon: <Flame className="w-4 h-4 text-emerald-400 animate-pulse" />,
          color: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
          desc: 'Taxa máxima de queima de gordura preservando massa magra.',
        };
      case 'MAINTENANCE_MARGIN':
        return {
          label: 'Margem de Manutenção',
          icon: <ShieldAlert className="w-4 h-4 text-amber-400" />,
          color: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
          desc: 'Acima do déficit alvo, mas ainda seguro abaixo do gasto de manutenção.',
        };
      case 'SURPLUS':
        return {
          label: 'Superávit Calórico',
          icon: <TrendingUp className="w-4 h-4 text-rose-400" />,
          color: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
          desc: 'Ingestão superior ao teto de manutenção diária.',
        };
      case 'BELOW_TARGET':
      default:
        return {
          label: 'Abaixo do Alvo',
          icon: <Sparkles className="w-4 h-4 text-sky-400" />,
          color: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
          desc: 'Faltam calorias para atingir a zona segura do protocolo.',
        };
    }
  };

  const badge = getZoneBadge();

  // Formatação amigável da data (ex: "Segunda-feira, 16 de Novembro")
  const formattedDate = new Date(currentDate + 'T00:00:00').toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });

  return (
    <div className="bg-slate-900/80 backdrop-blur border border-slate-800 rounded-3xl p-6 shadow-xl relative overflow-hidden">
      {/* Glow de fundo */}
      <div className="absolute -top-24 -right-24 w-72 h-72 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <span className="text-xs uppercase tracking-wider font-semibold text-slate-400 capitalize">
            {formattedDate}
          </span>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight flex items-center gap-3 mt-1">
            Plano Ativo: {activeTarget ? (activeTarget.targetSource === 'CALCULATED' ? 'Metabolismo Clínico' : 'Cutting de Precisão') : 'Sem Meta Ativa'}
          </h1>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto flex-wrap">
          <button
            type="button"
            onClick={() => setIsWizardOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-emerald-500/30 bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 text-xs font-semibold transition"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Wizard de Metas</span>
          </button>
          {hasActiveTarget && (
            <button
              type="button"
              onClick={handleResetTarget}
              disabled={isLoggingOut}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-slate-700 bg-slate-800/80 hover:bg-amber-500/15 hover:border-amber-500/30 text-slate-300 hover:text-amber-300 text-xs font-semibold transition disabled:opacity-50"
              title="Desativar meta atual e reabrir o Wizard"
            >
              {isLoggingOut ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin text-amber-400" />
              ) : (
                <LogOut className="w-3.5 h-3.5 text-slate-400" />
              )}
              <span>{isLoggingOut ? 'Resetando...' : 'Resetar Metas'}</span>
            </button>
          )}
          <div className={`inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border text-xs font-semibold ${badge.color}`}>
            {badge.icon}
            <span>{badge.label}</span>
          </div>
        </div>
      </div>

      {/* Calorias Totais em Destaque */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-end mb-6">
        <div className="md:col-span-2">
          <div className="flex items-baseline gap-2">
            <span className="text-4xl sm:text-5xl font-black text-white tracking-tight">
              {consumedEnergyKcal.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
            </span>
            <span className="text-lg text-slate-400 font-medium">
              / {targetKcalMin.toFixed(0)} - {targetKcalMax.toFixed(0)} kcal
            </span>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            {badge.desc}
          </p>
        </div>

        <div className="flex flex-col md:items-end justify-center bg-slate-800/40 border border-slate-700/40 rounded-2xl p-4">
          <span className="text-xs font-medium text-slate-400">Teto de Manutenção</span>
          <span className="text-xl font-bold text-slate-200 mt-0.5">
            {maintenanceKcalCeiling.toFixed(0)} kcal
          </span>
          <span className="text-xs text-slate-400 mt-0.5">
            {caloricZone.deltaToMaintenanceCeiling >= 0
              ? `${caloricZone.deltaToMaintenanceCeiling.toFixed(0)} kcal de margem`
              : `${Math.abs(caloricZone.deltaToMaintenanceCeiling).toFixed(0)} kcal acima`}
          </span>
        </div>
      </div>

      {/* Barra de 3 Zonas Progressivas */}
      <div className="space-y-2">
        <div className="relative h-4 bg-slate-800 rounded-full overflow-hidden p-0.5 flex">
          {/* Zona 1: Abaixo do Piso (Até Min) */}
          <div
            style={{ width: `${minZonePercent}%` }}
            className="h-full bg-slate-700/60 rounded-l-full relative"
            title={`Abaixo da meta: 0 a ${targetKcalMin} kcal`}
          />
          {/* Zona 2: Déficit Ótimo (Min a Max) */}
          <div
            style={{ width: `${maxZonePercent - minZonePercent}%` }}
            className="h-full bg-emerald-500/80"
            title={`Déficit Ótimo: ${targetKcalMin} a ${targetKcalMax} kcal`}
          />
          {/* Zona 3: Margem de Manutenção (Max a Teto) */}
          <div
            style={{ width: `${ceilingZonePercent - maxZonePercent}%` }}
            className="h-full bg-amber-500/80"
            title={`Manutenção: ${targetKcalMax} a ${maintenanceKcalCeiling} kcal`}
          />
          {/* Zona 4: Superávit (> Teto) */}
          <div
            className="h-full flex-1 bg-rose-500/80 rounded-r-full"
            title={`Superávit: Acima de ${maintenanceKcalCeiling} kcal`}
          />

          {/* Marcador atual do Usuário */}
          <div
            style={{ left: `calc(${progressPercent}% - 4px)` }}
            className="absolute top-0 bottom-0 w-2 bg-white rounded-full shadow-[0_0_10px_rgba(255,255,255,0.9)] transition-all duration-500 ease-out z-10"
          />
        </div>

        {/* Legenda das Zonas */}
        <div className="flex justify-between items-center text-[11px] font-medium text-slate-400 px-1 pt-1">
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-slate-600" />
            0 kcal
          </span>
          <span className="flex items-center gap-1 text-emerald-400">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            {targetKcalMin.toFixed(0)} kcal (Piso)
          </span>
          <span className="flex items-center gap-1 text-amber-400">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            {targetKcalMax.toFixed(0)} kcal (Teto Déficit)
          </span>
          <span className="flex items-center gap-1 text-rose-400">
            <span className="w-2 h-2 rounded-full bg-rose-500" />
            {maintenanceKcalCeiling.toFixed(0)} kcal (Manutenção)
          </span>
        </div>
      </div>

      <TargetWizardModal
        isOpen={isWizardOpen}
        onClose={() => setIsWizardOpen(false)}
        canClose={hasActiveTarget}
        mealsCount={mealsCount}
      />
    </div>
  );
}
