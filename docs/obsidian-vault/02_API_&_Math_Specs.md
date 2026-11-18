---
title: "02 - API & Mathematical Specifications"
project: "Precision Nutrition Engine (TACO + USDA)"
author: "Senior Software Architect & Data Engineer"
created_at: 2026-11-16
version: "1.0.0"
status: "APPROVED"
tags:
  - api
  - rest
  - math
  - nutrition
  - formulas
---

# 📐 02 - Especificações Matemáticas & Contratos de API REST

Este documento estabelece as leis matemáticas invioláveis do sistema e os contratos de interface REST para comunicação entre a API Spring Boot 3.3 e a aplicação cliente Next.js.

---

## 🔬 1. O Motor Matemático Nutricional

Todas as operações devem ser processadas em `java.math.BigDecimal` com precisão de pelo menos 4 casas decimais durante os cálculos intermediários, sendo arredondadas para 2 casas decimais no momento da persistência e serialização JSON (`RoundingMode.HALF_UP`).

### 1.1. Cálculo Linear Proporcional (Base 100g)
Todo alimento na tabela `foods` está estritamente normalizado para a porção de 100g. O consumo de uma porção arbitrária $W$ (em gramas) obedece à equação linear:

$$\text{Nutriente}_{\text{consumido}} = \left(\frac{\text{Nutriente}_{100\text{g}}}{100}\right) \times W$$

Onde $\text{Nutriente}$ representa:
- $\text{energy\_kcal}$ (kcal)
- $\text{protein\_g}$ (g)
- $\text{carbohydrate\_g}$ (g)
- $\text{lipid\_g}$ (g)
- $\text{fiber\_g}$ (g)
- Cada uma das 9 chaves do objeto JSONB de $\text{micronutrients}$ (mg ou mcg).

---

### 1.2. Auditoria de Atwater e Tolerância de Divergência ($\pm 15\%$)
A densidade calórica oficial de um alimento vem do laudo laboratorial cadastrado na tabela (`energy_kcal`). Como salvaguarda de integridade de dados (especialmente em dados inseridos manualmente ou via scraping), o sistema executa o cálculo de validação pelos fatores clássicos de Atwater:

$$\text{kcal}_{\text{Atwater}} = (4 \times \text{protein\_g}) + (4 \times \text{carbohydrate\_g}) + (9 \times \text{lipid\_g})$$

O percentual de desvio relativo $\delta_{\text{Atwater}}$ é dado por:

$$\delta_{\text{Atwater}} = \left| \frac{\text{energy\_kcal} - \text{kcal}_{\text{Atwater}}}{\text{energy\_kcal}} \right|$$

- **Regra de Auditoria:** Se $\delta_{\text{Atwater}} > 0.15$ ($15\%$), o alimento recebe um alerta de auditoria (`atwaterDivergence = true`), pois pode conter álcool não contabilizado, fibra insolúvel deduzida dos carboidratos totais ou erro no laudo original.

---

### 1.3. Algoritmo de Substituição Equivalente Bidirecional (Estratégia Híbrida Determinística)
Permite ao usuário substituir um Alimento A (peso planejado $W_A$ em gramas) por um Alimento B, suportando o parâmetro opcional `targetMacroOverride` (`PROTEIN`, `CARB`, `FAT`, `KCAL`).

#### Regra 1: Determinação do Macro-Alvo (Origem dita o alvo)
Quando `targetMacroOverride == null`:
- O macro que rege a substituição é o `dominant_macro` do **Alimento de Origem (A)**, pois o objetivo nutricional daquele slot na refeição é fornecer tal macronutriente (ex: ao substituir Peito de Frango [`PROTEIN`] por Ovo Cozido [`FAT`], a substituição busca igualar as gramas de proteína de A).

#### Regra 2: Desempate para Alimento de Origem `BALANCED`
Quando o Alimento A for classificado como `BALANCED` e `targetMacroOverride == null`:
- Se $\text{protein\_g} \ge 5.0\text{g} / 100\text{g}$, utiliza **`PROTEIN`** como fallback prioritário.
- Caso contrário, utiliza **Equivalência Isocalórica (`KCAL`)**.
- A qualquer momento o cliente pode forçar explicitamente `targetMacroOverride` (`PROTEIN`, `CARB`, `FAT` ou `KCAL`).

#### Passo 1: Cálculo da massa absoluta do macro-alvo (ou calorias) em A
Se o alvo for um macronutriente $M$ ($g$):
$$M_{A\_\text{total}} = \left( \frac{M_{A\_100\text{g}}}{100} \right) \times W_A$$

Se o alvo for isocalórico (`KCAL`):
$$\text{kcal}_{A\_\text{total}} = \left( \frac{\text{energy\_kcal}_{A\_100\text{g}}}{100} \right) \times W_A$$

#### Passo 2: Cálculo do peso necessário do Alimento B ($W_B$)
Para macronutriente:
$$W_B = \left( \frac{M_{A\_\text{total}}}{M_{B\_100\text{g}}} \right) \times 100$$

Para isocalórico:
$$W_B = \left( \frac{\text{kcal}_{A\_\text{total}}}{\text{energy\_kcal}_{B\_100\text{g}}} \right) \times 100$$

#### Regra 3: Proteção contra Divisão por Zero e Incompatibilidade Biológica
- Se $M_{B\_100\text{g}} < 0.5\text{g}$ (ou $\text{energy\_kcal}_{B\_100\text{g}} < 10.0\text{ kcal}$ para cálculo isocalórico), a substituição é inviável (exigiria quilos de alimento) e a API lança `IncompatibleFoodSubstitutionException` com status HTTP `422 Unprocessable Entity`.


---

### 1.4. Alerta de Desvio Calórico (*Calorie Drift Warning*)
Mesmo quando dois alimentos possuem o mesmo macro dominante (ex: Peito de Frango vs. Ovo de Galinha Inteiro, ambos `PROTEIN`), os macronutrientes secundários (gorduras ou carboidratos) podem ser radicalmente distintos, provocando uma explosão calórica indesejada no balanço diário.

O sistema calcula o impacto energético e de todos os macros:
$$\Delta\text{kcal} = \text{kcal}_B - \text{kcal}_A$$
$$\Delta\text{protein} = \text{protein}_B - \text{protein}_A$$
$$\Delta\text{carb} = \text{carb}_B - \text{carb}_A$$
$$\Delta\text{fat} = \text{fat}_B - \text{fat}_A$$

Onde $\text{kcal}_B$ é a caloria fornecida pelo Alimento B com peso $W_B$.

#### Critério do Alerta:
$$\text{calorieDriftWarning} = \left( \left| \frac{\Delta\text{kcal}}{\text{kcal}_A} \right| > 0.10 \right)$$

Se a variação for superior a $10\%$ do valor energético original de A, a flag `calorieDriftWarning` é marcada como `true` e a API retorna os deltas explícitos para que a interface avise o usuário com destaque visual.

---

### 1.5. Modelo de Zonas de Progresso Calórico (3 Zonas)
A barra superior do Dashboard utiliza 3 zonas delimitadas pelos limiares definidos em `daily_targets`:

1. **Zona Verde (Piso do Déficit / Faixa Alvo Ótima):**
   $$\text{target\_kcal\_min} \le \text{kcal}_{\text{consumido}} \le \text{target\_kcal\_max}$$
   Exemplo: Entre 2000.00 kcal e 2103.00 kcal.
2. **Zona Amarela (Margem de Segurança até Manutenção):**
   $$\text{target\_kcal\_max} < \text{kcal}_{\text{consumido}} \le \text{maintenance\_kcal\_ceiling}$$
   Exemplo: De 2103.01 kcal até 2500.00 kcal. O usuário comeu mais que a meta do cutting, porém ainda não ultrapassou o gasto energético total diário (TDEE).
3. **Zona Vermelha (Superávit / Rompimento de Teto):**
   $$\text{kcal}_{\text{consumido}} > \text{maintenance\_kcal\_ceiling}$$
   O consumo ultrapassou o teto de manutenção, acumulando superávit energético.

---

### 1.6. Balanço Hidroeletrolítico: Razão Sódio : Potássio (Na:K)
A razão entre a ingestão de sódio e potássio é um marcador clínico crítico de retenção hídrica, pressão arterial e equilíbrio osmótico intracelular vs. extracelular:

$$\text{Razão}_{\text{Na:K}} = \frac{\text{sodium\_mg}}{\text{potassium\_mg}}$$

- **Classificação Nutricional:**
  - $\text{Razão} \le 0.8$: **Excelente** (alta densidade de potássio vegetal, baixo sódio industrializado).
  - $0.8 < \text{Razão} \le 1.2$: **Adequada / Neutra**.
  - $\text{Razão} > 1.2$: **Alerta de Excesso de Sódio** (propensão a retenção hídrica e hipertensão).

---

### 1.7. Onboarding & Target Wizard: Equação de Mifflin-St Jeor & TDEE
Calcula o Gasto Energético Total Diário (TDEE) e a Taxa Metabólica Basal (BMR) a partir de parâmetros antropométricos e nível de atividade física.

#### Taxa Metabólica Basal (BMR):
- **Para Homens ($M$):**
  $$\text{BMR} = 10 \times \text{peso (kg)} + 6.25 \times \text{altura (cm)} - 5 \times \text{idade (anos)} + 5$$
- **Para Mulheres ($F$):**
  $$\text{BMR} = 10 \times \text{peso (kg)} + 6.25 \times \text{altura (cm)} - 5 \times \text{idade (anos)} - 161$$

#### Fator de Atividade Física ($f_{\text{act}}$):
- `SEDENTARY` (Sedentário, pouco ou nenhum exercício): $1.200$
- `LIGHT` (Exercício leve 1 a 3 dias/semana): $1.375$
- `MODERATE` (Exercício moderado 3 a 5 dias/semana): $1.550$
- `VERY_ACTIVE` (Exercício pesado 6 a 7 dias/semana): $1.725$
- `EXTRA_ACTIVE` (Treino diário intenso ou trabalho braçal duplo): $1.900$

#### Gasto Energético Total (TDEE):
$$\text{TDEE} = \text{BMR} \times f_{\text{act}}$$

*Exemplo de Calibração:* Homem de 20 anos, 68 kg, 1.73m (173 cm) com atividade moderada:
$$\text{BMR} = 10(68) + 6.25(173) - 5(20) + 5 = 680 + 1081.25 - 100 + 5 = 1666.25\text{ kcal}$$
$$\text{TDEE} = 1666.25 \times 1.55 = 2582.69\text{ kcal} \approx 2582\text{ kcal}$$

#### Distribuição de Macros por Objetivo (`DietGoal`):
1. **Perda de Gordura (`LOSE_FAT`):**
   - Déficit: $\text{targetKcalMax} = \text{TDEE} \times 0.80$ (-20%)
   - Piso: $\text{targetKcalMin} = \text{targetKcalMax} - 100\text{ kcal}$
   - Teto de Manutenção: $\text{maintenanceCeiling} = \text{TDEE}$
   - Proteína: $2.2\text{g} / \text{kg}$
   - Lipídios: $0.8\text{g} / \text{kg}$
   - Carboidratos: $[\text{targetKcalMax} - (4 \times \text{proteína}) - (9 \times \text{lipídios})] / 4$
2. **Manutenção (`MAINTAIN`):**
   - $\text{targetKcalMax} = \text{TDEE}$
   - $\text{targetKcalMin} = \text{TDEE} - 100\text{ kcal}$
   - $\text{maintenanceCeiling} = \text{TDEE} \times 1.10$
   - Proteína: $2.0\text{g} / \text{kg}$
   - Lipídios: $1.0\text{g} / \text{kg}$
   - Carboidratos: restante $\div 4$
3. **Hipertrofia Limpa (`CLEAN_BULK`):**
   - Superávit: $\text{targetKcalMax} = \text{TDEE} \times 1.10$ (+10%)
   - Piso: $\text{targetKcalMin} = \text{TDEE}$
   - $\text{maintenanceCeiling} = \text{targetKcalMax} + 200\text{ kcal}$
   - Proteína: $2.0\text{g} / \text{kg}$
   - Lipídios: $1.0\text{g} / \text{kg}$
   - Carboidratos: restante $\div 4$

---

### 1.8. Algoritmo de Troca Rápida Inteligente (Top 5 Menor Calorie Drift)
Dado um alimento selecionado $A$ com peso planejado $W_A$:
1. Identifica o grupo alimentar culinário ($A.\text{foodGroup}$). Se nulo, adota $A.\text{dominantMacro}$.
2. Recupera os alimentos elegíveis pertencentes ao mesmo grupo (ordenados por `popularity_score DESC`).
3. Para cada candidato $B$, executa o cálculo de equivalência do macronutriente dominante ($W_B = W_A \cdot \frac{M_A}{M_B}$).
4. Computa o desvio calórico absoluto: $|\Delta\text{kcal}| = |\text{kcal}_B - \text{kcal}_A|$.
5. Ordena todos os candidatos por menor $|\Delta\text{kcal}|$ ascendente e retorna os **Top 5** com menor desvio (menor propensão ao *Calorie Drift*).

---

## 🌐 2. Contratos de API RESTful (Spring Boot 3.3)

Todos os endpoints operam sob o prefixo `/api/v1` com cabeçalho `Content-Type: application/json; charset=UTF-8`.

---

### 2.1. Alimentos (Foods)

#### `GET /api/v1/foods/search`
Busca alimentos no banco de dados local com correspondência de trigramas (`pg_trgm`) e insensibilidade a acentos.

- **Query Parameters:**
  - `q` (string, obrigatório, min 2 chars): Termo de busca (ex: `frango grelhado`).
  - `source` (string, opcional): `TACO`, `USDA` ou `CUSTOM`.
  - `dominantMacro` (string, opcional): `PROTEIN`, `CARB`, `FAT`, `BALANCED`.
  - `page` (int, padrão 0): Página.
  - `size` (int, padrão 20): Quantidade.

- **Response 200 OK:**
```json
{
  "content": [
    {
      "id": "e4b2d308-41e9-4e78-9e67-d8c7c9808381",
      "name": "Frango, peito, sem pele, grelhado",
      "source": "TACO",
      "externalId": "287",
      "category": "Carnes e derivados",
      "dominantMacro": "PROTEIN",
      "servingSizeG": 100.00,
      "energyKcal": 159.00,
      "proteinG": 32.00,
      "carbohydrateG": 0.00,
      "lipidG": 2.50,
      "fiberG": 0.00,
      "micronutrients": {
        "calcium_mg": 2.0,
        "iron_mg": 0.4,
        "magnesium_mg": 28.0,
        "potassium_mg": 386.0,
        "sodium_mg": 50.0,
        "zinc_mg": 1.1,
        "vitamin_c_mg": 0.0,
        "vitamin_b12_mcg": 0.3,
        "cholesterol_mg": 89.0
      }
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "pageNumber": 0
}
```

---

#### `POST /api/v1/foods/usda-sync`
Realiza a consulta remota na API USDA FoodData Central (`https://api.nal.usda.gov/fdc/v1/foods/search`), normaliza o resultado para o contrato do sistema, persiste no PostgreSQL como cache local (`source = 'USDA'`) e retorna o alimento salvo.

- **Request Body:**
```json
{
  "fdcId": "171077"
}
```

- **Response 200 OK (ou 201 Created se foi inserido no cache):**
```json
{
  "id": "7fa12cb4-89d3-4813-91ee-4c3115eb57aa",
  "name": "Chicken breast, rotisserie, roasted",
  "source": "USDA",
  "externalId": "171077",
  "category": "Poultry Products",
  "dominantMacro": "PROTEIN",
  "servingSizeG": 100.00,
  "energyKcal": 165.00,
  "proteinG": 31.02,
  "carbohydrateG": 0.00,
  "lipidG": 3.57,
  "fiberG": 0.00,
  "micronutrients": {
    "calcium_mg": 11.0,
    "iron_mg": 0.88,
    "magnesium_mg": 25.0,
    "potassium_mg": 334.0,
    "sodium_mg": 316.0,
    "zinc_mg": 1.0,
    "vitamin_c_mg": 0.0,
    "vitamin_b12_mcg": 0.34,
    "cholesterol_mg": 85.0
  }
}
```

---

### 2.2. Motor de Substituição (Nutrition Engine)

#### `POST /api/v1/nutrition/substitute`
Calcula o peso equivalente do novo alimento mantendo o macro dominante, e avalia o impacto calórico e de macros secundários.

- **Request Body:**
```json
{
  "sourceFoodId": "e4b2d308-41e9-4e78-9e67-d8c7c9808381",
  "sourceWeightG": 150.00,
  "targetFoodId": "5c9e2b10-72a1-432d-98ff-83f12bbca450",
  "macroDominantOverride": "PROTEIN"
}
```

- **Response 200 OK:**
```json
{
  "sourceFood": {
    "id": "e4b2d308-41e9-4e78-9e67-d8c7c9808381",
    "name": "Frango, peito, sem pele, grelhado",
    "weightG": 150.00,
    "energyKcal": 238.50,
    "proteinG": 48.00,
    "carbohydrateG": 0.00,
    "lipidG": 3.75
  },
  "targetFood": {
    "id": "5c9e2b10-72a1-432d-98ff-83f12bbca450",
    "name": "Ovo, de galinha, inteiro, cozido",
    "calculatedWeightG": 369.23,
    "energyKcal": 539.08,
    "proteinG": 48.00,
    "carbohydrateG": 2.22,
    "lipidG": 37.66
  },
  "deltaKcal": 300.58,
  "deltaProteinG": 0.00,
  "deltaCarbohydrateG": 2.22,
  "deltaLipidG": 33.91,
  "calorieDriftPercent": 126.03,
  "calorieDriftWarning": true,
  "warningMessage": "Atenção: A substituição mantém 48.00g de proteína, porém aumenta o valor energético em +300.58 kcal (+126.0%) devido ao teor elevado de gordura do ovo."
}
```

---

### 2.3. Protocolo e Metas (Daily Targets)

#### `GET /api/v1/targets/active`
Obtém o protocolo nutricional ativo no momento.

- **Response 200 OK:**
```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "userId": "99999999-9999-9999-9999-999999999999",
  "isActive": true,
  "targetKcalMin": 2000.00,
  "targetKcalMax": 2103.00,
  "maintenanceKcalCeiling": 2500.00,
  "targetProteinG": 160.00,
  "targetCarbohydrateG": 220.00,
  "targetLipidG": 60.00,
  "targetFiberG": 30.00,
  "targetWaterMl": 3500.00,
  "targetMicronutrients": {
    "calcium_mg": 1000.0,
    "iron_mg": 14.0,
    "magnesium_mg": 400.0,
    "potassium_mg": 3500.0,
    "sodium_mg": 2000.0,
    "zinc_mg": 11.0,
    "vitamin_c_mg": 90.0,
    "vitamin_b12_mcg": 2.4,
    "cholesterol_mg": 300.0
  }
}
```

---

### 2.4. Template de Refeições (Meals)

#### `GET /api/v1/meals`
Retorna a lista ordenada de refeições prescritas com seus respectivos itens e opções equivalentes.

- **Response 200 OK:**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "name": "Café da Manhã",
    "orderIndex": 1,
    "targetTime": "08:00:00",
    "options": [
      {
        "optionNumber": 1,
        "totalKcal": 420.50,
        "totalProteinG": 32.00,
        "totalCarbG": 45.00,
        "totalLipidG": 12.50,
        "items": [
          {
            "id": "e1f2a3b4-5678-90ab-cdef-1234567890ab",
            "foodId": "e4b2d308-41e9-4e78-9e67-d8c7c9808381",
            "foodName": "Ovo, de galinha, inteiro, cozido",
            "weightG": 120.00
          }
        ]
      },
      {
        "optionNumber": 2,
        "totalKcal": 415.00,
        "totalProteinG": 33.00,
        "totalCarbG": 46.00,
        "totalLipidG": 11.00,
        "items": []
      }
    ]
  }
]
```

---

### 2.5. Diário de Consumo (Daily Logs)

#### `GET /api/v1/logs/{date}`
Retorna a visão diária consolidada (consumo realizado vs. metas, hidratação e lista de alimentos ingeridos com o flag `isOffDiet`).

- **Path Parameter:**
  - `date`: `YYYY-MM-DD` (ex: `2026-11-16`)

- **Response 200 OK:**
```json
{
  "id": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "userId": "99999999-9999-9999-9999-999999999999",
  "date": "2026-11-16",
  "waterConsumedMl": 2250.00,
  "waterTargetMl": 3500.00,
  "waterPercent": 64.29,
  "summary": {
    "totalKcal": 1850.40,
    "targetKcalMin": 2000.00,
    "targetKcalMax": 2103.00,
    "maintenanceKcalCeiling": 2500.00,
    "calorieZone": "BELOW_TARGET",
    "totalProteinG": 142.50,
    "targetProteinG": 160.00,
    "totalCarbG": 195.20,
    "targetCarbG": 220.00,
    "totalLipidG": 52.10,
    "targetLipidG": 60.00,
    "totalFiberG": 28.40,
    "targetFiberG": 30.00,
    "sodiumToPotassiumRatio": 0.65
  },
  "entries": [
    {
      "id": "f1e2d3c4-b5a6-9788-1234-abcdef567890",
      "mealId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "foodId": "e4b2d308-41e9-4e78-9e67-d8c7c9808381",
      "foodNameSnapshot": "Frango, peito, sem pele, grelhado",
      "weightConsumedG": 150.00,
      "consumedEnergyKcal": 238.50,
      "consumedProteinG": 48.00,
      "consumedCarbohydrateG": 0.00,
      "consumedLipidG": 3.75,
      "consumedFiberG": 0.00,
      "isOffDiet": false,
      "loggedAt": "2026-11-16T12:45:00Z"
    }
  ]
}
```

---

#### `PATCH /api/v1/logs/{date}/water`
Incrementa ou define o total de água consumida no dia.

- **Request Body:**
```json
{
  "mode": "INCREMENT",
  "amountMl": 250.00
}
```
*(Valores para `mode`: `INCREMENT` ou `SET`)*

- **Response 200 OK:**
```json
{
  "date": "2026-11-16",
  "waterConsumedMl": 2500.00,
  "waterTargetMl": 3500.00,
  "waterPercent": 71.43
}
```

---

#### `POST /api/v1/logs/{date}/check-meal`
Alterna o status de conclusão (toggle ON/OFF) de uma opção prescrita de refeição. Se a refeição já estiver checada com a mesma opção, remove os lançamentos; caso contrário, remove opções anteriores e registra os itens calculados proporcionalmente com `is_off_diet = false`.

- **Request Body:**
```json
{
  "mealId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "optionNumber": 1,
  "userId": "00000000-0000-0000-0000-000000000001"
}
```

- **Response 200 OK:** Retorna a entidade `DailyLog` atualizada com a lista de `entries`.

---

#### `POST /api/v1/logs/{date}/off-diet`
Registra consumo avulso ou fora da dieta (`is_off_diet = true`). Se `foodId` for informado, calcula os macros proporcionalmente ao peso; se for nulo, cria e vincula um alimento personalizado com os macronutrientes informados.

- **Request Body:**
```json
{
  "foodId": null,
  "customName": "Fatia de Pizza Margherita",
  "weightConsumedG": 120.00,
  "energyKcal": 320.00,
  "proteinG": 12.00,
  "carbohydrateG": 35.00,
  "lipidG": 15.00,
  "fiberG": 2.00,
  "userId": "00000000-0000-0000-0000-000000000001"
}
```

- **Response 201 Created:** Retorna o `DailyLogEntry` persistido.

---

#### `DELETE /api/v1/logs/entries/{entryId}`
Exclui uma entrada individual do diário.

- **Response 204 No Content**

---

### 2.6. Agregador Central de Dashboard

#### `GET /api/v1/dashboard`
Retorna em um único payload atômico todas as informações necessárias para renderizar a interface de usuário (estilo USFit): meta ativa, resumo de macronutrientes consumidos vs meta com zona calórica, matriz de micronutrientes com status de razão Na:K, as 5 refeições prescritas com suas opções calculadas e status de conclusão (checkbox) e lista de alimentos consumidos no dia.

- **Query Parameters:**
  - `date` (LocalDate, padrão hoje): `YYYY-MM-DD`
  - `userId` (UUID, opcional): Padrão `00000000-0000-0000-0000-000000000001`

- **Response 200 OK:** Retorna `DashboardResponse`.

---

### 2.7. Onboarding & Target Wizard (BMR & TDEE)

#### `POST /api/v1/targets/calculate`
Calcula BMR, TDEE, calorias e distribuição completa de macronutrientes sem persistir.

- **Request Body:**
```json
{
  "gender": "MALE",
  "ageYears": 20,
  "weightKg": 68.00,
  "heightCm": 173.00,
  "activityLevel": "MODERATE",
  "goal": "LOSE_FAT"
}
```

- **Response 200 OK:**
```json
{
  "bmrKcal": 1666.25,
  "tdeeKcal": 2582.69,
  "targetKcalMin": 1966.15,
  "targetKcalMax": 2066.15,
  "maintenanceKcalCeiling": 2582.69,
  "targetProteinG": 149.60,
  "targetCarbohydrateG": 243.64,
  "targetLipidG": 54.40,
  "targetFiberG": 30.00,
  "targetWaterMl": 3500.00,
  "targetMicronutrients": { ... },
  "targetSource": "CALCULATED"
}
```

#### `POST /api/v1/targets/calculate-and-apply`
Calcula as metas via Mifflin-St Jeor e ativa imediatamente como o novo protocolo nutricional do usuário (`targetSource = CALCULATED`), desativando o protocolo anterior.

- **Request Body:** Mesmo do endpoint `/calculate`.
- **Response 201 Created:** Retorna a entidade `DailyTarget` persistida e ativa.

---

### 2.8. Substituições Rápidas Culinárias (Quick Substitutions Top 5)

#### `GET /api/v1/nutrition/quick-substitutions`
Retorna as 5 melhores alternativas do mesmo grupo culinário com o menor desvio calórico (*Calorie Drift*).

- **Query Parameters:**
  - `foodId` (UUID, obrigatório): ID do alimento de origem.
  - `weightG` (BigDecimal, opcional, padrão 100.00): Peso planejado em gramas.
  - `targetMacroOverride` (DominantMacro, opcional): Sobrescrita de macro-alvo (`PROTEIN`, `CARB`, `FAT`, `KCAL`).
  - `limit` (int, opcional, padrão 5): Quantidade máxima de sugestões.

- **Response 200 OK:**
```json
[
  {
    "targetFood": {
      "id": "uuid...",
      "name": "Frango, sobrecoxa, sem pele, cozida",
      "calculatedWeightG": 162.50,
      "energyKcal": 271.38,
      "proteinG": 48.00,
      "carbohydrateG": 0.00,
      "lipidG": 8.75
    },
    "deltaKcal": 32.88,
    "deltaProteinG": 0.00,
    "deltaCarbohydrateG": 0.00,
    "deltaLipidG": 5.00,
    "calorieDriftPercent": 13.78,
    "calorieDriftWarning": true,
    "warningMessage": "Atenção: A substituição mantém 48.00g de proteína..."
  }
]
```

---

### 2.9. Manutenção & TTL (Admin Clean-up)

#### `POST /api/v1/admin/purge-old-logs`
Dispara manualmente o expurgo de registros diários anteriores a `CURRENT_DATE - 6` (executado também de forma automática via `@Scheduled` à meia-noite).

- **Response 200 OK:**
```json
{
  "purgedCount": 14,
  "cutoffDate": "2026-11-11",
  "message": "Expurgo de registros com TTL de 6 dias concluído com sucesso."
}
```


