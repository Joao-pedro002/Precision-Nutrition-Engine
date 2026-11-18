import {
  DashboardResponse,
  WaterUpdateResponse,
  WaterUpdateRequest,
  Food,
  SubstitutionRequest,
  SubstitutionResult,
  CheckMealRequest,
  OffDietEntryRequest,
  DailyLogEntry,
  TargetCalculationRequest,
  TargetCalculationResponse,
  QuickSubstitutionDto,
  DailyTarget,
} from '@/types/nutrition';

const API_BASE_URL = process.env.API_BASE_URL || process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1';

/**
 * Consulta o agregado do Dashboard diretamente do back-end Spring Boot.
 * Executado preferencialmente em React Server Components (RSC).
 */
export async function getDashboardData(date?: string): Promise<DashboardResponse> {
  const queryParam = date ? `?date=${encodeURIComponent(date)}` : '';
  const url = `${API_BASE_URL}/dashboard${queryParam}`;

  try {
    const response = await fetch(url, {
      cache: 'no-store', // Dados dinâmicos de consumo em tempo real
      headers: {
        'Accept': 'application/json',
      },
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Falha ao obter dashboard [HTTP ${response.status}]: ${errorText}`);
    }

    return await response.json();
  } catch (error) {
    console.error(`[API] Erro ao consultar dashboard em ${url}:`, error);
    throw error;
  }
}

/**
 * Atualiza o consumo de água do dia.
 */
export async function updateWaterApi(date: string, request: WaterUpdateRequest): Promise<WaterUpdateResponse> {
  const url = `${API_BASE_URL}/logs/${encodeURIComponent(date)}/water`;

  const response = await fetch(url, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao atualizar hidratação [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Busca alimentos no banco de dados local com trigramas e unaccent.
 * Suporta o parâmetro includeUsda para consulta sob demanda na USDA.
 */
export async function searchFoodsApi(query: string, includeUsda: boolean = false): Promise<Food[]> {
  const url = `${API_BASE_URL}/foods/search?q=${encodeURIComponent(query)}&includeUsda=${includeUsda}&size=20`;

  const response = await fetch(url, {
    cache: 'no-store',
    headers: { 'Accept': 'application/json' },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha na busca de alimentos [HTTP ${response.status}]: ${errorText}`);
  }

  const data = await response.json();
  return data.content || [];
}

/**
 * Calcula a substituição equivalente bidirecional via motor de cálculo matemático do Spring Boot.
 */
export async function calculateSubstitutionApi(request: SubstitutionRequest): Promise<SubstitutionResult> {
  const url = `${API_BASE_URL}/nutrition/substitute`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha no cálculo de substituição [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Alterna o status de checagem da opção de refeição (toggle idempotente).
 */
export async function checkMealOptionApi(date: string, request: CheckMealRequest): Promise<unknown> {
  const url = `${API_BASE_URL}/logs/${encodeURIComponent(date)}/check-meal`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao registrar refeição [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Desmarca uma refeição (remove registros do diário, inclusive fora da dieta).
 */
export async function uncheckMealOptionApi(date: string, request: CheckMealRequest): Promise<unknown> {
  const url = `${API_BASE_URL}/logs/${encodeURIComponent(date)}/uncheck-meal`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao desmarcar refeição [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Registra lançamento avulso fora da dieta para o dia.
 */
export async function addOffDietEntryApi(date: string, request: OffDietEntryRequest): Promise<DailyLogEntry> {
  const url = `${API_BASE_URL}/logs/${encodeURIComponent(date)}/off-diet`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao registrar entrada fora da dieta [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Aplica a substituição definitiva em um template de refeição: remove o item anterior e adiciona o novo.
 */
export async function applyItemSubstitutionApi(
  mealId: string,
  currentItemId: string,
  newFoodId: string,
  newWeightG: number,
  optionNumber: number
): Promise<void> {
  await fetch(`${API_BASE_URL}/meals/items/${encodeURIComponent(currentItemId)}`, {
    method: 'DELETE',
  });

  await fetch(`${API_BASE_URL}/meals/${encodeURIComponent(mealId)}/items`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify({
      foodId: newFoodId,
      optionNumber,
      weightG: newWeightG,
    }),
  });
}

/**
 * Simula o cálculo de BMR, TDEE e macros via Target Wizard (Mifflin-St Jeor).
 */
export async function calculateTargetWizardApi(
  request: TargetCalculationRequest
): Promise<TargetCalculationResponse> {
  const response = await fetch(`${API_BASE_URL}/targets/calculate`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao calcular metas [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Calcula e aplica as metas calculadas via Target Wizard como protocolo ativo no back-end.
 */
export async function applyCalculatedTargetApi(
  request: TargetCalculationRequest
): Promise<DailyTarget> {
  const response = await fetch(`${API_BASE_URL}/targets/calculate-and-apply`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao aplicar novo protocolo ativo [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Salva uma meta personalizada diretamente (ex: após ajuste no slider de calorias).
 */
export async function applyCustomTargetApi(targetData: {
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
  const response = await fetch(`${API_BASE_URL}/targets`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(targetData),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao salvar meta personalizada [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Consulta as 5 melhores alternativas de substituição rápida do mesmo grupo culinário com menor Calorie Drift.
 */
export async function getQuickSubstitutionsApi(
  foodId: string,
  weightG?: number,
  targetMacroOverride?: string | null,
  limit: number = 5
): Promise<QuickSubstitutionDto[]> {
  const params = new URLSearchParams({
    foodId,
    limit: limit.toString(),
  });
  if (weightG) params.append('weightG', weightG.toString());
  if (targetMacroOverride) params.append('targetMacroOverride', targetMacroOverride);

  const response = await fetch(`${API_BASE_URL}/nutrition/quick-substitutions?${params.toString()}`, {
    cache: 'no-store',
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao buscar trocas rápidas [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Desativa a meta ativa do usuário no back-end (Logout / Reset de Sessão).
 */
export async function deactivateActiveTargetApi(userId?: string): Promise<void> {
  const queryParam = userId ? `?userId=${encodeURIComponent(userId)}` : '';
  const response = await fetch(`${API_BASE_URL}/targets/deactivate${queryParam}`, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao desativar meta [HTTP ${response.status}]: ${errorText}`);
  }
}
