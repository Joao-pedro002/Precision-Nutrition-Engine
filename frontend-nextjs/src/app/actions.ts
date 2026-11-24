'use server';

import { revalidatePath } from 'next/cache';
import {
  updateWaterApi,
  checkMealOptionApi,
  uncheckMealOptionApi,
  addOffDietEntryApi,
  calculateSubstitutionApi,
  applyItemSubstitutionApi,
  searchFoodsApi,
  calculateTargetWizardApi,
  applyCalculatedTargetApi,
  applyCustomTargetApi,
  getQuickSubstitutionsApi,
  deactivateActiveTargetApi,
  recordWeightApi,
  createCustomFoodApi,
  updateMealItemWeightApi,
  removeMealItemApi,
  addMealOptionItemApi,
  createMealApi,
  deleteMealApi,
} from '@/lib/api';
import {
  WaterUpdateResponse,
  SubstitutionRequest,
  SubstitutionResult,
  OffDietEntryRequest,
  Food,
  TargetCalculationRequest,
  TargetCalculationResponse,
  QuickSubstitutionDto,
  DailyTarget,
} from '@/types/nutrition';

/**
 * Server Action: Atualização de Hidratação
 */
export async function updateWaterServerAction(
  date: string,
  mode: 'INCREMENT' | 'SET',
  amountMl: number
): Promise<WaterUpdateResponse> {
  const result = await updateWaterApi(date, { mode, amountMl });
  revalidatePath('/');
  return result;
}

/**
 * Server Action: Check de Refeição ("Na Dieta")
 */
export async function checkMealServerAction(
  date: string,
  mealId: string,
  optionNumber: number
): Promise<void> {
  await checkMealOptionApi(date, { mealId, optionNumber });
  revalidatePath('/');
}

/**
 * Server Action: Desmarcar Refeição ou Desfazer Refeição Livre
 */
export async function uncheckMealServerAction(
  date: string,
  mealId: string
): Promise<void> {
  await uncheckMealOptionApi(date, { mealId, optionNumber: 1 });
  revalidatePath('/');
}

/**
 * Server Action: Lançamento Fora da Dieta ("Refeição Livre")
 */
export async function addOffDietServerAction(
  date: string,
  request: OffDietEntryRequest
): Promise<void> {
  await addOffDietEntryApi(date, request);
  revalidatePath('/');
}

/**
 * Server Action: Cálculo de Substituição Equivalente em tempo real
 */
export async function substituteFoodServerAction(
  request: SubstitutionRequest
): Promise<SubstitutionResult> {
  return await calculateSubstitutionApi(request);
}

/**
 * Server Action: Busca rápida de alimentos (com ou sem USDA)
 */
export async function searchFoodsServerAction(
  query: string,
  includeUsda: boolean = false
): Promise<Food[]> {
  return await searchFoodsApi(query, includeUsda);
}

/**
 * Server Action: Aplicação permanente de substituição no cardápio
 */
export async function applyItemSubstitutionServerAction(
  mealId: string,
  currentItemId: string,
  newFoodId: string,
  newWeightG: number,
  optionNumber: number
): Promise<void> {
  await applyItemSubstitutionApi(mealId, currentItemId, newFoodId, newWeightG, optionNumber);
  revalidatePath('/');
}

/**
 * Server Action: Cálculo de BMR/TDEE via Target Wizard
 */
export async function calculateTargetWizardServerAction(
  request: TargetCalculationRequest
): Promise<TargetCalculationResponse> {
  return await calculateTargetWizardApi(request);
}

/**
 * Server Action: Aplicação direta das metas calculadas (Mifflin-St Jeor)
 */
export async function applyCalculatedTargetServerAction(
  request: TargetCalculationRequest
): Promise<DailyTarget> {
  const result = await applyCalculatedTargetApi(request);
  revalidatePath('/');
  return result;
}

/**
 * Server Action: Aplicação de metas ajustadas manualmente pelo slider
 */
export async function applyCustomTargetServerAction(targetData: {
  userId?: string;
  targetKcalMin: number;
  targetKcalMax: number;
  maintenanceKcalCeiling: number;
  targetProteinG: number;
  targetCarbohydrateG: number;
  targetLipidG: number;
  targetFiberG: number;
  targetWaterMl: number;
  isActive: boolean;
}): Promise<DailyTarget> {
  const result = await applyCustomTargetApi(targetData);
  revalidatePath('/');
  return result;
}

/**
 * Server Action: Consulta de sugestões inteligentes de troca rápida (Top 5)
 */
export async function getQuickSubstitutionsServerAction(
  foodId: string,
  weightG?: number,
  targetMacroOverride?: string | null
): Promise<QuickSubstitutionDto[]> {
  return await getQuickSubstitutionsApi(foodId, weightG, targetMacroOverride, 5);
}

/**
 * Server Action: Desativa a meta ativa (Logout / Reset de Sessão)
 */
export async function deactivateActiveTargetServerAction(userId?: string): Promise<void> {
  await deactivateActiveTargetApi(userId);
  revalidatePath('/');
}

/**
 * Server Action: Registra ou atualiza o peso corporal de uma data
 */
export async function recordWeightServerAction(
  logDate: string,
  weightKg: number
): Promise<import('@/types/nutrition').WeightLogEntry> {
  const result = await recordWeightApi(logDate, weightKg);
  revalidatePath('/');
  return result;
}

/**
 * Server Action: Cadastra um alimento customizado por 100g
 */
export async function createCustomFoodServerAction(
  request: import('@/types/nutrition').CustomFoodRequest
): Promise<Food> {
  const created = await createCustomFoodApi(request);
  revalidatePath('/');
  return created;
}

/**
 * Server Action: Atualiza a gramagem de um item da refeição prescrita
 */
export async function updateMealItemWeightServerAction(
  itemId: string,
  weightG: number
): Promise<void> {
  await updateMealItemWeightApi(itemId, weightG);
  revalidatePath('/');
}

/**
 * Server Action: Remove um item de uma opção de refeição prescrita
 */
export async function removeMealItemServerAction(itemId: string): Promise<void> {
  await removeMealItemApi(itemId);
  revalidatePath('/');
}

/**
 * Server Action: Adiciona um alimento a uma opção da refeição prescrita
 */
export async function addMealOptionItemServerAction(
  mealId: string,
  optionNumber: number,
  foodId: string,
  weightG: number
): Promise<void> {
  await addMealOptionItemApi(mealId, optionNumber, foodId, weightG);
  revalidatePath('/');
}

/**
 * Server Action: Cria uma nova refeição no plano ativo
 */
export async function createMealServerAction(
  name: string,
  targetTime?: string | null
): Promise<void> {
  await createMealApi(name, targetTime);
  revalidatePath('/');
}

/**
 * Server Action: Remove uma refeição do molde do plano ativo
 */
export async function deleteMealServerAction(mealId: string): Promise<void> {
  await deleteMealApi(mealId);
  revalidatePath('/');
}
