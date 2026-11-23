'use client';

import React from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon } from 'lucide-react';

interface DateSelectorProps {
  currentDate: string;
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseIsoDateLocal(dateStr: string): Date {
  const [year, month, day] = dateStr.split('-').map(Number);
  if (!year || !month || !day) {
    return new Date();
  }
  return new Date(year, month - 1, day);
}

export function DateSelector({ currentDate }: DateSelectorProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  const handleNavigate = (daysDelta: number) => {
    const current = parseIsoDateLocal(currentDate);
    current.setDate(current.getDate() + daysDelta);
    const newDateStr = formatLocalDate(current);

    const params = new URLSearchParams(searchParams.toString());
    params.set('date', newDateStr);
    router.push(`/?${params.toString()}`);
  };

  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newDateStr = e.target.value;
    if (/^\d{4}-\d{2}-\d{2}$/.test(newDateStr)) {
      const params = new URLSearchParams(searchParams.toString());
      params.set('date', newDateStr);
      router.push(`/?${params.toString()}`);
    }
  };

  const isToday = currentDate === formatLocalDate(new Date());

  return (
    <div className="flex items-center justify-between bg-slate-900/60 border border-slate-800 rounded-2xl p-2 px-3 shadow-md">
      <button
        type="button"
        onClick={() => handleNavigate(-1)}
        className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 hover:text-white transition-colors"
        title="Dia Anterior"
        aria-label="Dia Anterior"
      >
        <ChevronLeft className="w-5 h-5" />
      </button>

      <div className="flex items-center gap-2">
        <label className="relative flex items-center cursor-pointer">
          <CalendarIcon className="w-4 h-4 text-emerald-400 mr-1.5 pointer-events-none" />
          <input
            type="date"
            value={currentDate}
            onChange={handleDateChange}
            aria-label="Selecionar data do diário"
            className="bg-transparent text-sm font-bold text-white border-none focus:outline-none cursor-pointer"
          />
        </label>
        {isToday && (
          <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 border border-emerald-500/20">
            Hoje
          </span>
        )}
      </div>

      <button
        type="button"
        onClick={() => handleNavigate(1)}
        className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 hover:text-white transition-colors"
        title="Próximo Dia"
        aria-label="Próximo Dia"
      >
        <ChevronRight className="w-5 h-5" />
      </button>
    </div>
  );
}
