'use client';

import React, { useState, useRef, useEffect, useTransition } from 'react';
import {
  Sparkles,
  X,
  Activity,
  CheckCircle2,
  Sliders,
  RotateCcw,
  Loader2,
  Info,
} from 'lucide-react';
import {
  Gender,
  ActivityLevel,
  DietGoal,
  TargetCalculationRequest,
  TargetCalculationResponse,
} from '@/types/nutrition';
import {
  calculateTargetWizardServerAction,
  applyCalculatedTargetServerAction,
  applyCustomTargetServerAction,
} from '@/app/actions';

interface TargetWizardModalProps {
  isOpen: boolean;
  onClose: () => void;
  canClose?: boolean;
}

export function TargetWizardModal({ isOpen, onClose, canClose = true }: TargetWizardModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [isPending, startTransition] = useTransition();

  // Form states com prevenção de leading zeros
  const [gender, setGender] = useState<Gender>('MALE');
  const [ageYears, setAgeYears] = useState<string | number>(25);
  const [weightKg, setWeightKg] = useState<string | number>(70);
  const [heightCm, setHeightCm] = useState<string | number>(175);
  const [activityLevel, setActivityLevel] = useState<ActivityLevel>('MODERATE');
  const [goal, setGoal] = useState<DietGoal>('LOSE_FAT');
  const [proteinMultiplier, setProteinMultiplier] = useState<number>(2.0);

  // Calculation & Slider states
  const [calculatedResult, setCalculatedResult] = useState<TargetCalculationResponse | null>(null);
  const [sliderKcal, setSliderKcal] = useState<number>(2000);
  const [adjustedProtein, setAdjustedProtein] = useState<number>(150);
  const [adjustedLipid, setAdjustedLipid] = useState<number>(55);
  const [adjustedCarb, setAdjustedCarb] = useState<number>(225);
  const [isCustomMode, setIsCustomMode] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Alerta clínico dinâmico para o multiplicador de proteína
  const getProteinBadge = (multiplier: number) => {
    if (multiplier < 1.8) {
      return {
        text: '⚠️ Risco de perda de massa magra em déficit calórico prolongado.',
        style: 'bg-amber-500/10 text-amber-300 border-amber-500/30',
      };
    } else if (multiplier <= 2.2) {
      return {
        text: '✅ Faixa ideal (padrão ouro) para hipertrofia e retenção magra.',
        style: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30',
      };
    } else {
      return {
        text: '🛡️ Saciedade extrema, mas pode reduzir carboidratos e energia no treino.',
        style: 'bg-sky-500/10 text-sky-300 border-sky-500/30',
      };
    }
  };

  const proteinBadge = getProteinBadge(proteinMultiplier);

  const handleGoalChange = (newGoal: DietGoal) => {
    setGoal(newGoal);
    setProteinMultiplier(newGoal === 'LOSE_FAT' ? 2.2 : 2.0);
  };

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
    }
  }, [isOpen]);

  // Recalcular macros quando o slider calórico é movimentado
  const handleSliderChange = (newKcal: number) => {
    setSliderKcal(newKcal);
    setIsCustomMode(true);

    if (calculatedResult) {
      // Mantém a proteína e lipídios recomendados por kg e ajusta os carboidratos
      const proteinKcal = adjustedProtein * 4;
      const lipidKcal = adjustedLipid * 9;
      const remainingKcal = Math.max(0, newKcal - proteinKcal - lipidKcal);
      const newCarb = Math.round((remainingKcal / 4) * 10) / 10;
      setAdjustedCarb(Math.max(20, newCarb));
    }
  };

  const handleCalculate = () => {
    setErrorMessage(null);
    setSuccessMessage(null);

    const request: TargetCalculationRequest = {
      gender,
      ageYears: Number(ageYears) || 25,
      weightKg: Number(weightKg) || 70,
      heightCm: Number(heightCm) || 175,
      activityLevel,
      goal,
      proteinMultiplier,
    };

    startTransition(async () => {
      try {
        const result = await calculateTargetWizardServerAction(request);
        setCalculatedResult(result);
        setSliderKcal(result.targetKcalMax);
        setAdjustedProtein(result.targetProteinG);
        setAdjustedLipid(result.targetLipidG);
        setAdjustedCarb(result.targetCarbohydrateG);
        setIsCustomMode(false);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Falha ao calcular metas nutricionais.';
        setErrorMessage(msg);
      }
    });
  };

  const handleApply = () => {
    setErrorMessage(null);
    setSuccessMessage(null);

    startTransition(async () => {
      try {
        if (!isCustomMode && calculatedResult) {
          // Salva diretamente o modelo clínico de Mifflin-St Jeor
          const request: TargetCalculationRequest = {
            gender,
            ageYears: Number(ageYears) || 25,
            weightKg: Number(weightKg) || 70,
            heightCm: Number(heightCm) || 175,
            activityLevel,
            goal,
            proteinMultiplier,
          };
          await applyCalculatedTargetServerAction(request);
        } else if (calculatedResult) {
          // Salva o modelo ajustado pelo slider
          const targetKcalMax = sliderKcal;
          const targetKcalMin = Math.max(1000, targetKcalMax - 100);
          const maintenanceKcalCeiling = Math.max(targetKcalMax, calculatedResult.tdeeKcal);

          await applyCustomTargetServerAction({
            targetKcalMin,
            targetKcalMax,
            maintenanceKcalCeiling,
            targetProteinG: adjustedProtein,
            targetCarbohydrateG: adjustedCarb,
            targetLipidG: adjustedLipid,
            targetFiberG: calculatedResult.targetFiberG,
            targetWaterMl: calculatedResult.targetWaterMl,
            isActive: true,
          });
        }

        setSuccessMessage('Novo protocolo ativo aplicado com sucesso!');
        setTimeout(() => {
          onClose();
          setSuccessMessage(null);
        }, 1200);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Falha ao salvar meta.';
        setErrorMessage(msg);
      }
    });
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onCancel={(e) => {
        if (!canClose) {
          e.preventDefault();
        }
      }}
      className="backdrop:bg-black/75 backdrop:backdrop-blur-sm bg-transparent p-0 m-auto max-w-2xl w-full text-slate-100 rounded-2xl outline-none"
    >
      <div className="bg-[#0b101b] border border-slate-800 shadow-2xl rounded-2xl p-6 sm:p-8 max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-800/80 pb-4 mb-6">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-emerald-500/10 border border-emerald-500/20 rounded-xl text-emerald-400">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold tracking-tight text-white flex items-center gap-2">
                Onboarding & Target Wizard
                <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 font-semibold border border-emerald-500/20">
                  Mifflin-St Jeor
                </span>
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">
                Calcule seu BMR, TDEE e protocolo de macros de alta precisão clínica.
              </p>
            </div>
          </div>
          {canClose ? (
            <button
              onClick={onClose}
              className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800/60 transition"
              title="Fechar"
            >
              <X className="w-5 h-5" />
            </button>
          ) : (
            <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-300 border border-amber-500/20">
              Configuração Obrigatória
            </span>
          )}
        </div>

        {/* Formulário de Parâmetros Antropométricos */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          {/* Gênero */}
          <div className="col-span-2 sm:col-span-2">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
              Gênero Biológico
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setGender('MALE')}
                className={`py-2 px-3 text-xs font-semibold rounded-xl border transition ${
                  gender === 'MALE'
                    ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                Masculino
              </button>
              <button
                type="button"
                onClick={() => setGender('FEMALE')}
                className={`py-2 px-3 text-xs font-semibold rounded-xl border transition ${
                  gender === 'FEMALE'
                    ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                Feminino
              </button>
            </div>
          </div>

          {/* Idade */}
          <div className="col-span-1">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
              Idade (anos)
            </label>
            <input
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              value={typeof ageYears === 'string' ? ageYears : String(ageYears)}
              onChange={(e) => {
                const val = e.target.value.trim();
                setAgeYears(val === '' ? '' : val.replace(/^0+(?=\d)/, ''));
              }}
              className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 transition"
            />
          </div>

          {/* Peso */}
          <div className="col-span-1">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
              Peso (kg)
            </label>
            <input
              type="text"
              inputMode="decimal"
              value={typeof weightKg === 'string' ? weightKg : String(weightKg)}
              onChange={(e) => {
                const val = e.target.value.trim();
                setWeightKg(val === '' ? '' : val.replace(/^0+(?=\d)/, ''));
              }}
              className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 transition"
            />
          </div>

          {/* Altura */}
          <div className="col-span-2">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
              Altura (cm ou metros)
            </label>
            <input
              type="text"
              inputMode="decimal"
              value={typeof heightCm === 'string' ? heightCm : String(heightCm)}
              onChange={(e) => {
                const val = e.target.value.trim();
                setHeightCm(val === '' ? '' : val.replace(/^0+(?=\d)/, ''));
              }}
              placeholder="Ex: 175 ou 1.75"
              className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 transition"
            />
          </div>

          {/* Nível de Atividade */}
          <div className="col-span-2">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
              Nível de Atividade Física
            </label>
            <select
              value={activityLevel}
              onChange={(e) => setActivityLevel(e.target.value as ActivityLevel)}
              className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-emerald-500 transition"
            >
              <option value="SEDENTARY">Sedentário (Pouco ou nenhum exercício)</option>
              <option value="LIGHT">Leve (Exercício 1 a 3 dias/sem)</option>
              <option value="MODERATE">Moderado (Exercício 3 a 5 dias/sem)</option>
              <option value="VERY_ACTIVE">Muito Ativo (Exercício pesado 6-7 dias/sem)</option>
              <option value="EXTRA_ACTIVE">Extremamente Ativo (Treino duplo / pesado)</option>
            </select>
          </div>

          {/* Objetivo */}
          <div className="col-span-2 sm:col-span-4">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
              Objetivo Estratégico da Dieta
            </label>
            <div className="grid grid-cols-3 gap-2">
              <button
                type="button"
                onClick={() => handleGoalChange('LOSE_FAT')}
                className={`py-2 px-3 text-xs font-semibold rounded-xl border text-center transition ${
                  goal === 'LOSE_FAT'
                    ? 'bg-amber-500/15 border-amber-500 text-amber-300'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                🔥 Queimar Gordura (-20%)
              </button>
              <button
                type="button"
                onClick={() => handleGoalChange('MAINTAIN')}
                className={`py-2 px-3 text-xs font-semibold rounded-xl border text-center transition ${
                  goal === 'MAINTAIN'
                    ? 'bg-blue-500/15 border-blue-500 text-blue-300'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                ⚖️ Manutenção (TDEE)
              </button>
              <button
                type="button"
                onClick={() => handleGoalChange('CLEAN_BULK')}
                className={`py-2 px-3 text-xs font-semibold rounded-xl border text-center transition ${
                  goal === 'CLEAN_BULK'
                    ? 'bg-emerald-500/15 border-emerald-500 text-emerald-300'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                💪 Bulking Limpo (+10%)
              </button>
            </div>
          </div>

          {/* Multiplicador Dinâmico de Proteína */}
          <div className="col-span-2 sm:col-span-4 bg-slate-900/60 border border-slate-800 p-4 rounded-xl">
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-semibold text-slate-300 flex items-center gap-2">
                <span>Multiplicador de Proteína:</span>
                <span className="text-sm font-bold text-emerald-400 font-mono">
                  {proteinMultiplier.toFixed(1)} g/kg
                </span>
              </label>
              <span className="text-[11px] text-slate-400">
                Padrão recomendado: {goal === 'LOSE_FAT' ? '2.2 g/kg' : '2.0 g/kg'}
              </span>
            </div>

            <input
              type="range"
              min="1.5"
              max="3.0"
              step="0.1"
              value={proteinMultiplier}
              onChange={(e) => setProteinMultiplier(Number(e.target.value))}
              className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-emerald-500"
            />

            <div className="flex justify-between text-[10px] text-slate-500 mt-1.5 font-mono">
              <span>1.5 g/kg</span>
              <span>2.0 g/kg (Padrão)</span>
              <span>3.0 g/kg</span>
            </div>

            {/* Badge Clínico Dinâmico */}
            <div className={`mt-3 p-2.5 rounded-xl border text-xs font-medium ${proteinBadge.style}`}>
              {proteinBadge.text}
            </div>
          </div>
        </div>

        {/* Botão de Calcular */}
        <button
          type="button"
          onClick={handleCalculate}
          disabled={isPending}
          className="w-full py-2.5 px-4 bg-slate-800 hover:bg-slate-700 text-white text-sm font-semibold rounded-xl border border-slate-700 transition flex items-center justify-center gap-2 mb-6 shadow-sm disabled:opacity-60"
        >
          {isPending ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin text-emerald-400" />
              <span>Calculando Metabolismo Clínico no Spring Boot...</span>
            </>
          ) : (
            <>
              <Activity className="w-4 h-4 text-emerald-400" />
              <span>Calcular Taxas Metabólicas (Mifflin-St Jeor)</span>
            </>
          )}
        </button>

        {/* Erros / Avisos */}
        {errorMessage && (
          <div className="p-3 mb-6 bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs rounded-xl flex items-center gap-2">
            <Info className="w-4 h-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div className="p-3 mb-6 bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs rounded-xl flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{successMessage}</span>
          </div>
        )}

        {/* Resultados Calculados */}
        {calculatedResult && (
          <div className="space-y-6 pt-2 border-t border-slate-800/80">
            {/* Metas Primárias */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <div className="bg-slate-900/80 border border-slate-800 p-3.5 rounded-xl">
                <span className="text-[11px] font-semibold text-slate-400 block uppercase">Taxa Basal (BMR)</span>
                <span className="text-xl font-bold text-white mt-1 block">
                  {Math.round(calculatedResult.bmrKcal)} <span className="text-xs text-slate-400 font-normal">kcal</span>
                </span>
                <span className="text-[10px] text-slate-500">Mínimo biológico vital</span>
              </div>
              <div className="bg-slate-900/80 border border-slate-800 p-3.5 rounded-xl">
                <span className="text-[11px] font-semibold text-slate-400 block uppercase">Gasto Total (TDEE)</span>
                <span className="text-xl font-bold text-emerald-400 mt-1 block">
                  {Math.round(calculatedResult.tdeeKcal)} <span className="text-xs text-slate-400 font-normal">kcal</span>
                </span>
                <span className="text-[10px] text-slate-500">Gasto real com atividades</span>
              </div>
              <div className="col-span-2 sm:col-span-1 bg-slate-900/80 border border-slate-800 p-3.5 rounded-xl">
                <span className="text-[11px] font-semibold text-slate-400 block uppercase">Meta Diária Sugerida</span>
                <span className="text-xl font-bold text-amber-400 mt-1 block">
                  {Math.round(sliderKcal)} <span className="text-xs text-slate-400 font-normal">kcal</span>
                </span>
                <span className="text-[10px] text-slate-500">Faixa ideal de corte</span>
              </div>
            </div>

            {/* O Slider Interativo de Ajuste Manual */}
            <div className="bg-slate-900/60 border border-slate-800 p-4 rounded-xl">
              <div className="flex items-center justify-between mb-2">
                <label className="text-xs font-semibold text-slate-300 flex items-center gap-2">
                  <Sliders className="w-3.5 h-3.5 text-emerald-400" />
                  Ajuste Fino Calórico (Slider Manual):
                </label>
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-emerald-400">{sliderKcal} kcal</span>
                  {isCustomMode && (
                    <button
                      type="button"
                      onClick={() => handleSliderChange(calculatedResult.targetKcalMax)}
                      className="text-[10px] text-slate-400 hover:text-white flex items-center gap-1 transition"
                      title="Restaurar sugestão clínica"
                    >
                      <RotateCcw className="w-2.5 h-2.5" /> Reset
                    </button>
                  )}
                </div>
              </div>

              <input
                type="range"
                min="1200"
                max="5000"
                step="25"
                value={sliderKcal}
                onChange={(e) => handleSliderChange(Number(e.target.value))}
                className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-emerald-500"
              />
              <div className="flex justify-between text-[10px] text-slate-500 mt-1.5 font-mono">
                <span>1200 kcal</span>
                <span>Déficit / Manutenção</span>
                <span>5000 kcal</span>
              </div>
            </div>

            {/* Macros Resultantes */}
            <div className="grid grid-cols-3 gap-3">
              <div className="bg-slate-900/90 border border-emerald-500/20 p-3 rounded-xl text-center">
                <span className="text-[11px] font-bold text-emerald-400 uppercase block">Proteínas</span>
                <span className="text-lg font-bold text-white mt-0.5 block">{adjustedProtein}g</span>
                <span className="text-[10px] text-slate-400">{Math.round(adjustedProtein * 4)} kcal</span>
              </div>
              <div className="bg-slate-900/90 border border-blue-500/20 p-3 rounded-xl text-center">
                <span className="text-[11px] font-bold text-blue-400 uppercase block">Carboidratos</span>
                <span className="text-lg font-bold text-white mt-0.5 block">{adjustedCarb}g</span>
                <span className="text-[10px] text-slate-400">{Math.round(adjustedCarb * 4)} kcal</span>
              </div>
              <div className="bg-slate-900/90 border border-amber-500/20 p-3 rounded-xl text-center">
                <span className="text-[11px] font-bold text-amber-400 uppercase block">Gorduras</span>
                <span className="text-lg font-bold text-white mt-0.5 block">{adjustedLipid}g</span>
                <span className="text-[10px] text-slate-400">{Math.round(adjustedLipid * 9)} kcal</span>
              </div>
            </div>

            {/* Botão de Salvar Protocolo */}
            <div className="pt-2">
              <button
                type="button"
                onClick={handleApply}
                disabled={isPending}
                className="w-full py-3 px-4 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm rounded-xl transition flex items-center justify-center gap-2 shadow-lg shadow-emerald-600/20 disabled:opacity-50"
              >
                {isPending ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin text-white" />
                    <span>Aplicando Protocolo...</span>
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="w-4 h-4" />
                    <span>
                      {isCustomMode
                        ? `Salvar Meta Personalizada (${sliderKcal} kcal)`
                        : 'Ativar Protocolo Clínico Recomendado'}
                    </span>
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </dialog>
  );
}
