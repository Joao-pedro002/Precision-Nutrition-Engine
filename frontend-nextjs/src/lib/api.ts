import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { AUTH_COOKIE_NAME } from '@/lib/auth';
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
  WeightLogEntry,
  WeightHistoryResponse,
  CustomFoodRequest,
} from '@/types/nutrition';

function resolveApiBaseUrl(): string {
  const isServer = typeof window === 'undefined';
  const raw =
    (isServer ? process.env.INTERNAL_API_URL || process.env.API_BASE_URL : undefined) ||
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    'http://localhost:8080';

  const trimmed = raw.replace(/\/+$/, '');
  return trimmed.endsWith('/api/v1') ? trimmed : `${trimmed}/api/v1`;
}

const API_BASE_URL = resolveApiBaseUrl();

function isNextRedirectError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'digest' in error &&
    typeof (error as { digest?: unknown }).digest === 'string' &&
    (error as { digest: string }).digest.startsWith('NEXT_REDIRECT')
  );
}

/**
 * Wrapper centralizado para chamadas autenticadas ao back-end Spring Boot.
 * Lê o cookie HttpOnly no servidor, injeta Authorization: Bearer <token> e redireciona para /login em HTTP 401.
 */
async function authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = cookies().get(AUTH_COOKIE_NAME)?.value;
  const headers = new Headers(init.headers);

  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...init,
    headers,
  });

  if (response.status === 401) {
    redirect('/login');
  }

  return response;
}

export interface LoginApiResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: {
    id: string;
    email: string;
    name: string | null;
    role: 'USER' | 'ADMIN';
    active: boolean;
  };
}

/**
 * Autentica credenciais no endpoint público POST /api/v1/auth/login.
 */
export async function loginApi(email: string, password: string): Promise<LoginApiResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    cache: 'no-store',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 400) {
      throw new Error('E-mail ou senha inválidos');
    }
    throw new Error(`Falha ao autenticar [HTTP ${response.status}]`);
  }

  return await response.json();
}

/**
 * Consulta o agregado do Dashboard diretamente do back-end Spring Boot.
 */
export async function getDashboardData(date?: string): Promise<DashboardResponse> {
  const queryParam = date ? `?date=${encodeURIComponent(date)}` : '';
  const url = `${API_BASE_URL}/dashboard${queryParam}`;

  try {
    const response = await authorizedFetch(url, { cache: 'no-store' });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Falha ao obter dashboard [HTTP ${response.status}]: ${errorText}`);
    }

    return await response.json();
  } catch (error) {
    if (isNextRedirectError(error)) {
      throw error;
    }
    console.error(`[API] Erro ao consultar dashboard em ${url}:`, error);
    throw error;
  }
}

/**
 * Atualiza o consumo de água do dia.
 */
export async function updateWaterApi(date: string, request: WaterUpdateRequest): Promise<WaterUpdateResponse> {
  const url = `${API_BASE_URL}/logs/${encodeURIComponent(date)}/water`;

  const response = await authorizedFetch(url, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
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
 */
export async function searchFoodsApi(query: string, includeUsda: boolean = false): Promise<Food[]> {
  const url = `${API_BASE_URL}/foods/search?q=${encodeURIComponent(query)}&includeUsda=${includeUsda}&size=20`;

  const response = await authorizedFetch(url, { cache: 'no-store' });

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

  const response = await authorizedFetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
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

  const response = await authorizedFetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
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

  const response = await authorizedFetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
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

  const response = await authorizedFetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao registrar entrada fora da dieta [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Aplica a substituição definitiva em um template de refeição.
 */
export async function applyItemSubstitutionApi(
  mealId: string,
  currentItemId: string,
  newFoodId: string,
  newWeightG: number,
  optionNumber: number
): Promise<void> {
  const delRes = await authorizedFetch(`${API_BASE_URL}/meals/items/${encodeURIComponent(currentItemId)}`, {
    method: 'DELETE',
  });
  if (!delRes.ok) {
    const errorText = await delRes.text();
    throw new Error(`Falha ao remover item antigo [HTTP ${delRes.status}]: ${errorText}`);
  }

  const addRes = await authorizedFetch(`${API_BASE_URL}/meals/${encodeURIComponent(mealId)}/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      foodId: newFoodId,
      optionNumber,
      weightG: newWeightG,
    }),
  });
  if (!addRes.ok) {
    const errorText = await addRes.text();
    throw new Error(`Falha ao adicionar alimento substituto [HTTP ${addRes.status}]: ${errorText}`);
  }
}

/**
 * Simula o cálculo de BMR, TDEE e macros via Target Wizard (Mifflin-St Jeor).
 */
export async function calculateTargetWizardApi(
  request: TargetCalculationRequest
): Promise<TargetCalculationResponse> {
  const response = await authorizedFetch(`${API_BASE_URL}/targets/calculate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
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
  const response = await authorizedFetch(`${API_BASE_URL}/targets/calculate-and-apply`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
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
  generateBaseMeals?: boolean;
}): Promise<DailyTarget> {
  const response = await authorizedFetch(`${API_BASE_URL}/targets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(targetData),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao salvar meta personalizada [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Consulta as 5 melhores alternativas de substituição rápida do mesmo grupo culinário.
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

  const response = await authorizedFetch(`${API_BASE_URL}/nutrition/quick-substitutions?${params.toString()}`, {
    cache: 'no-store',
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao buscar trocas rápidas [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Desativa a meta ativa do usuário no back-end.
 */
export async function deactivateActiveTargetApi(): Promise<void> {
  const response = await authorizedFetch(`${API_BASE_URL}/targets/deactivate`, {
    method: 'POST',
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao desativar meta [HTTP ${response.status}]: ${errorText}`);
  }
}

/**
 * Registra ou atualiza o peso corporal de uma data específica (idempotente).
 */
export async function recordWeightApi(
  logDate: string,
  weightKg: number
): Promise<WeightLogEntry> {
  const response = await authorizedFetch(`${API_BASE_URL}/weight`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ logDate, weightKg }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao registrar peso corporal [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Busca o histórico recente de peso corporal e variação em relação à última pesagem.
 */
export async function getWeightHistoryApi(): Promise<WeightHistoryResponse> {
  const response = await authorizedFetch(`${API_BASE_URL}/weight/history`, {
    cache: 'no-store',
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao buscar histórico de peso [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Cadastra um alimento personalizado (source = CUSTOM) com valores nutricionais por 100g.
 */
export async function createCustomFoodApi(request: CustomFoodRequest): Promise<Food> {
  const response = await authorizedFetch(`${API_BASE_URL}/foods/custom`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao cadastrar alimento customizado [HTTP ${response.status}]: ${errorText}`);
  }

  return await response.json();
}

/**
 * Atualiza a gramagem (g) de um item prescrito em uma refeição.
 */
export async function updateMealItemWeightApi(itemId: string, weightG: number): Promise<void> {
  const response = await authorizedFetch(`${API_BASE_URL}/meals/items/${itemId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ weightG }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao atualizar gramagem do item [HTTP ${response.status}]: ${errorText}`);
  }
}

/**
 * Remove um item prescrito de uma opção de refeição.
 */
export async function removeMealItemApi(itemId: string): Promise<void> {
  const response = await authorizedFetch(`${API_BASE_URL}/meals/items/${itemId}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao remover item da refeição [HTTP ${response.status}]: ${errorText}`);
  }
}

/**
 * Adiciona um alimento existente (TACO, USDA ou CUSTOM) a uma opção específica da refeição.
 */
export async function addMealOptionItemApi(
  mealId: string,
  optionNumber: number,
  foodId: string,
  weightG: number
): Promise<void> {
  const response = await authorizedFetch(`${API_BASE_URL}/meals/${mealId}/options/${optionNumber}/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ foodId, weightG }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao adicionar alimento na refeição [HTTP ${response.status}]: ${errorText}`);
  }
}

/**
 * Cria uma nova refeição prescrita no plano ativo do usuário.
 */
export async function createMealApi(name: string, targetTime?: string | null): Promise<void> {
  const payload: { name: string; targetTime?: string } = { name };
  if (targetTime && /^\d{2}:\d{2}(:\d{2})?$/.test(targetTime.trim())) {
    payload.targetTime = targetTime.trim();
  }

  const response = await authorizedFetch(`${API_BASE_URL}/meals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao criar refeição [HTTP ${response.status}]: ${errorText}`);
  }
}

/**
 * Remove uma refeição do molde do plano ativo.
 */
export async function deleteMealApi(mealId: string): Promise<void> {
  const response = await authorizedFetch(`${API_BASE_URL}/meals/${mealId}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Falha ao excluir refeição [HTTP ${response.status}]: ${errorText}`);
  }
}

export interface AiChatMessage {
  role: 'user' | 'model';
  text: string;
}

export interface AiChatApiResult {
  ok: true;
  reply: string;
  model: string;
}

export interface AiChatApiError {
  ok: false;
  status: number;
  error: string;
}

/**
 * Consulta o Copiloto Nutricional com IA (POST /api/v1/ai/chat) usando o cookie HttpOnly.
 */
export async function askNutritionAiApi(
  message: string,
  date?: string,
  history?: AiChatMessage[]
): Promise<AiChatApiResult | AiChatApiError> {
  try {
    const response = await authorizedFetch(`${API_BASE_URL}/ai/chat`, {
      method: 'POST',
      cache: 'no-store',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message,
        date: date || undefined,
        history: history && history.length > 0 ? history.slice(-12) : undefined,
      }),
    });

    if (!response.ok) {
      let backendMessage = '';
      try {
        const errJson = await response.json();
        if (typeof errJson?.message === 'string') {
          backendMessage = errJson.message;
        }
      } catch {
        // fallback para mensagem amigável por status
      }

      if (response.status === 429) {
        return {
          ok: false,
          status: 429,
          error:
            backendMessage ||
            'Você atingiu o limite de perguntas por minuto. Aguarde alguns instantes e tente novamente.',
        };
      }
      if (response.status === 503) {
        return {
          ok: false,
          status: 503,
          error:
            backendMessage ||
            'O assistente de IA está temporariamente indisponível ou ainda não foi configurado no servidor.',
        };
      }
      return {
        ok: false,
        status: response.status,
        error: backendMessage || `Não foi possível consultar a IA no momento (HTTP ${response.status}).`,
      };
    }

    const data = await response.json();
    return {
      ok: true,
      reply: typeof data?.reply === 'string' ? data.reply : '',
      model: typeof data?.model === 'string' ? data.model : 'gemini-2.5-flash',
    };
  } catch (error: unknown) {
    if (isNextRedirectError(error)) {
      throw error;
    }
    return {
      ok: false,
      status: 503,
      error: 'Falha de conexão com o servidor. Verifique sua internet e tente novamente.',
    };
  }
}
