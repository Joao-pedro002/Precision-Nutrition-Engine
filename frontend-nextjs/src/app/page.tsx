import React from 'react';
import { getDashboardData } from '@/lib/api';
import { CalorieHeader } from '@/components/CalorieHeader';
import { MacroDonutCharts } from '@/components/MacroDonutCharts';
import { WaterTracker } from '@/components/WaterTracker';
import { MicronutrientMatrix } from '@/components/MicronutrientMatrix';
import { DateSelector } from '@/components/DateSelector';
import { MealCards } from '@/components/MealCards';
import { Sparkles, AlertCircle } from 'lucide-react';

interface PageProps {
  searchParams?: {
    date?: string;
  };
}

export const dynamic = 'force-dynamic';

export default async function DashboardPage({ searchParams }: PageProps) {
  const currentDate = searchParams?.date || new Date().toISOString().split('T')[0];

  let dashboardData;
  let fetchError = null;

  try {
    dashboardData = await getDashboardData(currentDate);
  } catch (error: unknown) {
    fetchError = error instanceof Error ? error.message : 'Falha ao conectar com o back-end Spring Boot (Porta 8080).';
  }

  return (
    <main className="max-w-5xl mx-auto px-4 sm:px-6 py-6 sm:py-8 space-y-6">
      {/* Barra de Navegação Superior */}
      <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-slate-800/60">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-lg shadow-emerald-500/20">
            <div className="w-full h-full bg-slate-950 rounded-[14px] flex items-center justify-center">
              <Sparkles className="w-5 h-5 text-emerald-400" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-base font-black tracking-tight text-white">
                PRECISION NUTRITION
              </span>
              <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                TACO + USDA
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Motor Matemático de Alta Fidelidade (USFit Engine)
            </p>
          </div>
        </div>

        <DateSelector currentDate={currentDate} />
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
          />

          {/* 2. Grid Principal: Gráficos de Anel (Donuts) e Rastreador de Hidratação */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-6">
              <MacroDonutCharts summary={dashboardData.summary} />
            </div>

            <div className="space-y-6">
              <WaterTracker
                currentDate={currentDate}
                initialWaterMl={dashboardData.summary.waterConsumedMl}
                targetWaterMl={dashboardData.summary.targetWaterMl}
              />
            </div>
          </div>

          {/* 3. Matriz de Micronutrientes & Termômetro da Razão Na : K */}
          <MicronutrientMatrix matrix={dashboardData.micronutrients} />

          {/* 4. Gestor de Refeições Prescritas com Multi-Opções e Modal de Substituição */}
          <MealCards meals={dashboardData.meals} currentDate={currentDate} />
        </div>
      )}
    </main>
  );
}
