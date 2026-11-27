import React from 'react';
import { getDashboardData, getWeightHistoryApi } from '@/lib/api';
import { CalorieHeader } from '@/components/CalorieHeader';
import { MacroDonutCharts, FiberProgressBar } from '@/components/MacroDonutCharts';
import { WaterTracker } from '@/components/WaterTracker';
import { WeightTracker } from '@/components/WeightTracker';
import { MicronutrientMatrix } from '@/components/MicronutrientMatrix';
import { DateSelector } from '@/components/DateSelector';
import { LogoutButton } from '@/components/LogoutButton';
import { MealCards } from '@/components/MealCards';
import { Sparkles, AlertCircle } from 'lucide-react';
import { WeightHistoryResponse } from '@/types/nutrition';

interface PageProps {
  searchParams?: {
    date?: string;
  };
}

export const dynamic = 'force-dynamic';

function resolveSafeDate(rawDate?: string): string {
  if (rawDate && /^\d{4}-\d{2}-\d{2}$/.test(rawDate)) {
    return rawDate;
  }
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function isNextRedirectError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'digest' in error &&
    typeof (error as { digest?: unknown }).digest === 'string' &&
    (error as { digest: string }).digest.startsWith('NEXT_REDIRECT')
  );
}

export default async function DashboardPage({ searchParams }: PageProps) {
  const currentDate = resolveSafeDate(searchParams?.date);

  let dashboardData;
  let weightHistory: WeightHistoryResponse | null = null;
  let fetchError = null;

  try {
    const [dashboard, weight] = await Promise.all([
      getDashboardData(currentDate),
      getWeightHistoryApi().catch((err) => {
        if (isNextRedirectError(err)) throw err;
        return null;
      }),
    ]);
    dashboardData = dashboard;
    weightHistory = weight;
  } catch (error: unknown) {
    if (isNextRedirectError(error)) {
      throw error;
    }
    fetchError = error instanceof Error ? error.message : 'Falha ao conectar com o back-end Spring Boot (Porta 8080).';
  }

  return (
    <main className="max-w-5xl mx-auto px-4 sm:px-6 py-3 sm:py-8 space-y-4 sm:space-y-6">
      {/* Barra de Navegação Superior */}
      <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 sm:gap-4 pb-2.5 sm:pb-2 border-b border-slate-800/60">
        <div className="flex items-center justify-between gap-2 w-full sm:w-auto">
          <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
            <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl sm:rounded-2xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-lg shadow-emerald-500/20 shrink-0">
              <div className="w-full h-full bg-slate-950 rounded-[10px] sm:rounded-[14px] flex items-center justify-center">
                <Sparkles className="w-4 h-4 sm:w-5 sm:h-5 text-emerald-400" aria-hidden="true" />
              </div>
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 sm:gap-2 flex-wrap">
                <span className="text-sm sm:text-base font-black tracking-tight text-white leading-tight">
                  PRECISION NUTRITION
                </span>
                <span className="text-[9px] sm:text-[10px] uppercase font-bold tracking-wider px-1.5 sm:px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 whitespace-nowrap">
                  TACO + USDA
                </span>
              </div>
              <p className="text-[11px] sm:text-xs text-slate-400 leading-snug">
                Motor Matemático de Alta Fidelidade (USFit Engine)
              </p>
            </div>
          </div>

          <LogoutButton compact className="sm:hidden" />
        </div>

        <div className="w-full sm:w-auto flex items-center gap-2.5 sm:self-auto">
          <div className="w-full sm:w-auto">
            <DateSelector currentDate={currentDate} />
          </div>
          <LogoutButton className="hidden sm:inline-flex" />
        </div>
      </header>

      {/* Alerta de Conexão se o Back-end estiver desligado */}
      {fetchError && (
        <div className="bg-rose-500/10 border border-rose-500/20 rounded-2xl p-4 flex items-start gap-3 text-rose-300">
          <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <div className="text-xs space-y-1">
            <p className="font-bold">Aviso de Conexão com o Back-end:</p>
            <p className="text-rose-400/90">{fetchError}</p>
            <p className="text-slate-400">
              Certifique-se de que a aplicação Spring Boot está em execução em <code>http://localhost:8080</code>.
            </p>
          </div>
        </div>
      )}

      {/* Conteúdo Principal do Dashboard */}
      {dashboardData && (
        <div className="space-y-6">
          {/* 1. Cabeçalho Calórico e Barra de 3 Zonas Progressivas */}
          <CalorieHeader
            summary={dashboardData.summary}
            activeTarget={dashboardData.activeTarget}
            currentDate={currentDate}
            mealsCount={dashboardData.meals.length}
          />

          {/* 2. Grid Principal Simétrico: Anéis de Macros + Hidratação + Fibras em largura total */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-stretch">
            <div className="lg:col-span-2">
              <MacroDonutCharts summary={dashboardData.summary} />
            </div>

            <div>
              <WaterTracker
                currentDate={currentDate}
                initialWaterMl={dashboardData.summary.waterConsumedMl}
                targetWaterMl={dashboardData.summary.targetWaterMl}
              />
            </div>

            <div className="col-span-full">
              <FiberProgressBar
                consumedFiberG={dashboardData.summary.consumedFiberG}
                targetFiberG={dashboardData.summary.targetFiberG}
              />
            </div>
          </div>

          {/* 2.5. Tracker de Peso Corporal */}
          <WeightTracker key={currentDate} currentDate={currentDate} weightHistory={weightHistory} />

          {/* 3. Matriz de Micronutrientes & Termômetro da Razão Na : K */}
          <MicronutrientMatrix matrix={dashboardData.micronutrients} />

          {/* 4. Gestor de Refeições Prescritas com Multi-Opções e Modal de Substituição */}
          <MealCards
            meals={dashboardData.meals}
            currentDate={currentDate}
            loggedEntries={dashboardData.loggedEntries}
          />
        </div>
      )}
    </main>
  );
}
