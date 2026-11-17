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
