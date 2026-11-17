'use server';

import { revalidatePath } from 'next/cache';
import {
  updateWaterApi,
  checkMealOptionApi,
  addOffDietEntryApi,
  calculateSubstitutionApi,
  applyItemSubstitutionApi,
  searchFoodsApi,
} from '@/lib/api';
import {
  WaterUpdateResponse,
  SubstitutionRequest,
  SubstitutionResult,
  OffDietEntryRequest,
  Food,
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
 * Server Action: Check / Uncheck de Refeição (Toggle "Na Dieta")
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
