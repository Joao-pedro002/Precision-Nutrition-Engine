---
title: "01 - Data Contracts, DDL & JSONB Schema"
project: "Precision Nutrition Engine (TACO + USDA)"
author: "Senior Software Architect & Data Engineer"
created_at: 2026-11-16
version: "1.0.0"
status: "APPROVED"
tags:
  - database
  - ddl
  - postgresql
  - jsonb
  - data-contract
---

# 📜 01 - Contratos de Dados, DDL PostgreSQL & Schema JSONB

Este documento é a **Fonte Única da Verdade (SSOT)** estrutural do banco de dados relacional e dos contratos semi-estruturados do sistema.
> [!IMPORTANT]
> Nenhuma entidade JPA (Java), DTO de entrada/saída (Spring Boot), interface TypeScript (Next.js) ou script de migração pode divergir dos nomes, tipos de dados e restrições aqui especificados.

---

## 🔬 1. Contrato Estrito do Objeto JSONB de Micronutrientes

O campo `micronutrients` (na tabela `foods`), `target_micronutrients` (na tabela `daily_targets`) e `consumed_micronutrients` (na tabela `daily_log_entries`) devem obedecer rigorosamente à seguinte estrutura JSON, com chaves em `snake_case` e sufixo de unidade de medida explícito:

```json
{
  "calcium_mg": 0.0,
  "iron_mg": 0.0,
  "magnesium_mg": 0.0,
  "potassium_mg": 0.0,
  "sodium_mg": 0.0,
  "zinc_mg": 0.0,
  "vitamin_c_mg": 0.0,
  "vitamin_b12_mcg": 0.0,
  "cholesterol_mg": 0.0
}
```

### 1.1. Detalhamento e Regras dos Campos
| Chave JSONB | Tipo | Unidade | Descrição / Papel Nutricional | Padrão Ausente |
| :--- | :--- | :--- | :--- | :--- |
| `calcium_mg` | `number` | mg (miligrama) | Cálcio total (saúde óssea e contração muscular) | `0.0` |
| `iron_mg` | `number` | mg (miligrama) | Ferro total (transporte de oxigênio / hemoglobina) | `0.0` |
| `magnesium_mg` | `number` | mg (miligrama) | Magnésio total (síntese proteica e função neuromuscular) | `0.0` |
| `potassium_mg` | `number` | mg (miligrama) | Potássio total (balanço hidroeletrolítico e vasodilatação) | `0.0` |
| `sodium_mg` | `number` | mg (miligrama) | Sódio total (pressão osmótica e retenção de fluidos) | `0.0` |
| `zinc_mg` | `number` | mg (miligrama) | Zinco total (imunidade e modulação hormonal) | `0.0` |
| `vitamin_c_mg` | `number` | mg (miligrama) | Vitamina C / Ácido ascórbico (antioxidante e síntese de colágeno) | `0.0` |
| `vitamin_b12_mcg` | `number` | mcg (micrograma) | Vitamina B12 / Cobalamina (hematopoiese e mielinização) | `0.0` |
| `cholesterol_mg` | `number` | mg (miligrama) | Colesterol dietético total | `0.0` |

---

## 🗄️ 2. Especificação Relacional das Tabelas (PostgreSQL 16)

### 2.1. Tabela `foods` (Catálogo de Alimentos Normalizados para 100g)
Armazena os alimentos cadastrados via carga ETL (TACO), via sincronização remota com cache (USDA FoodData Central) ou cadastrados manualmente pelo usuário (`CUSTOM`).

| Coluna | Tipo SQL | Nulo? | Padrão | Restrições & Observações |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | NÃO | `uuid_generate_v4()` | Chave primária |
| `name` | `VARCHAR(255)` | NÃO | - | Nome descritivo do alimento |
| `source` | `VARCHAR(20)` | NÃO | - | `CHECK (source IN ('TACO', 'USDA', 'CUSTOM'))` |
| `external_id` | `VARCHAR(100)` | SIM | `NULL` | ID de origem (ex: código TACO `1` ou USDA `fdcId` `171077`) |
| `category` | `VARCHAR(100)` | SIM | `NULL` | Categoria de alimentos (ex: 'Carnes e derivados', 'Cereais') |
| `dominant_macro` | `VARCHAR(20)` | NÃO | - | `CHECK (dominant_macro IN ('PROTEIN', 'CARB', 'FAT', 'BALANCED'))` |
| `serving_size_g` | `NUMERIC(8,2)` | NÃO | `100.00` | Base de normalização. Sempre 100.00g para consistência |
| `energy_kcal` | `NUMERIC(8,2)` | NÃO | - | `CHECK (energy_kcal >= 0)` - Densidade energética do laudo |
| `protein_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (protein_g >= 0)` - Proteína total em 100g |
| `carbohydrate_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (carbohydrate_g >= 0)` - Carboidrato disponível em 100g |
| `lipid_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (lipid_g >= 0)` - Gorduras totais em 100g |
| `fiber_g` | `NUMERIC(8,2)` | NÃO | `0.00` | `CHECK (fiber_g >= 0)` - Fibras alimentares em 100g |
| `micronutrients` | `JSONB` | NÃO | `'{}'::jsonb` | Objeto JSONB em conformidade com o Contrato 1 |
| `created_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de criação do registro |
| `updated_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data da última alteração |

#### Índices de `foods`:
- `CREATE INDEX idx_foods_micronutrients_gin ON foods USING GIN (micronutrients jsonb_path_ops);`
- `CREATE INDEX idx_foods_name_trgm ON foods USING GIN (immutable_unaccent(name) gin_trgm_ops);`
- `CREATE UNIQUE INDEX uq_foods_source_external_id ON foods (source, external_id) WHERE external_id IS NOT NULL;`
- `CREATE INDEX idx_foods_dominant_macro ON foods (dominant_macro);`

---

### 2.2. Tabela `daily_targets` (Protocolos Nutricionais e Zonas Calóricas)
Configuração de metas diárias do usuário. Utiliza um *Partial Unique Index* para garantir que exista apenas uma meta ativa por usuário em um dado momento.

| Coluna | Tipo SQL | Nulo? | Padrão | Restrições & Observações |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | NÃO | `uuid_generate_v4()` | Chave primária |
| `user_id` | `UUID` | NÃO | - | Identificador do usuário proprietário |
| `is_active` | `BOOLEAN` | NÃO | `false` | Se é o protocolo vigente no app |
| `target_kcal_min` | `NUMERIC(8,2)` | NÃO | - | Piso do Déficit (ex: 2000.00 kcal) |
| `target_kcal_max` | `NUMERIC(8,2)` | NÃO | - | Teto do Déficit / Meta ideal (ex: 2103.00 kcal) |
| `maintenance_kcal_ceiling` | `NUMERIC(8,2)` | NÃO | - | Teto de Manutenção antes do Superávit (ex: 2500.00 kcal) |
| `target_protein_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (target_protein_g >= 0)` |
| `target_carbohydrate_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (target_carbohydrate_g >= 0)` |
| `target_lipid_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (target_lipid_g >= 0)` |
| `target_fiber_g` | `NUMERIC(8,2)` | NÃO | `30.00` | `CHECK (target_fiber_g >= 0)` |
| `target_water_ml` | `NUMERIC(8,2)` | NÃO | `3500.00` | `CHECK (target_water_ml >= 0)` - Meta diária de água |
| `target_micronutrients` | `JSONB` | NÃO | `'{}'::jsonb` | Metas diárias de micronutrientes (RDA/AI) |
| `created_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de cadastro |
| `updated_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de alteração |

#### Restrições & Índices de `daily_targets`:
- `CONSTRAINT chk_kcal_zones CHECK (target_kcal_min <= target_kcal_max AND target_kcal_max <= maintenance_kcal_ceiling)`
- `CREATE UNIQUE INDEX uq_daily_targets_single_active ON daily_targets (user_id) WHERE is_active = true;`

---

### 2.3. Tabela `meals` (Template de Refeições do Plano)
Define a estrutura base das refeições prescritas (ex: Café da Manhã, Almoço, Lanche, Jantar).

| Coluna | Tipo SQL | Nulo? | Padrão | Restrições & Observações |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | NÃO | `uuid_generate_v4()` | Chave primária |
| `user_id` | `UUID` | NÃO | - | Identificador do usuário |
| `name` | `VARCHAR(100)` | NÃO | - | Ex: 'Café da Manhã', 'Almoço', 'Ceia' |
| `order_index` | `INT` | NÃO | `0` | Ordem de exibição visual nos cards do app |
| `target_time` | `TIME` | SIM | `NULL` | Horário sugerido (ex: 08:00:00) |
| `created_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de cadastro |
| `updated_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de atualização |

---

### 2.4. Tabela `meal_items` (Itens do Plano com Suporte a Multi-Opção)
Representa os alimentos componentes de cada refeição prescrita, permitindo opções equivalentes cadastradas pelo nutricionista (`option_number`: 1 = Opção 1, 2 = Opção 2).

| Coluna | Tipo SQL | Nulo? | Padrão | Restrições & Observações |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | NÃO | `uuid_generate_v4()` | Chave primária |
| `meal_id` | `UUID` | NÃO | - | `FOREIGN KEY REFERENCES meals(id) ON DELETE CASCADE` |
| `food_id` | `UUID` | NÃO | - | `FOREIGN KEY REFERENCES foods(id) ON DELETE RESTRICT` |
| `option_number` | `INT` | NÃO | `1` | `CHECK (option_number >= 1)` (1 = Opção 1, 2 = Opção 2) |
| `weight_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (weight_g > 0)` - Peso planejado em gramas |
| `created_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de cadastro |

#### Índices de `meal_items`:
- `CREATE INDEX idx_meal_items_meal_option ON meal_items (meal_id, option_number);`

---

### 2.5. Tabela `daily_logs` (Registro do Consumo Diário e Hidratação)
Armazena a visão agregada do dia real executado pelo usuário.

| Coluna | Tipo SQL | Nulo? | Padrão | Restrições & Observações |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | NÃO | `uuid_generate_v4()` | Chave primária |
| `user_id` | `UUID` | NÃO | - | Usuário proprietário do log |
| `date` | `DATE` | NÃO | - | Data do diário (AAAA-MM-DD) |
| `water_consumed_ml` | `NUMERIC(8,2)` | NÃO | `0.00` | `CHECK (water_consumed_ml >= 0)` - Água consumida |
| `notes` | `TEXT` | SIM | `NULL` | Anotações do dia (energia, digestão, sono) |
| `created_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de criação |
| `updated_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Data de atualização |

#### Restrições & Índices de `daily_logs`:
- `CREATE UNIQUE INDEX uq_daily_logs_user_date ON daily_logs (user_id, date);`

---

### 2.6. Tabela `daily_log_entries` (Snapshot Imutável dos Alimentos Consumidos)
Cada alimento consumido no dia gera uma linha nesta tabela com todos os seus macros e micronutrientes pré-calculados e salvos de forma imutável.

| Coluna | Tipo SQL | Nulo? | Padrão | Restrições & Observações |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | NÃO | `uuid_generate_v4()` | Chave primária |
| `daily_log_id` | `UUID` | NÃO | - | `FOREIGN KEY REFERENCES daily_logs(id) ON DELETE CASCADE` |
| `meal_id` | `UUID` | SIM | `NULL` | `FOREIGN KEY REFERENCES meals(id) ON DELETE SET NULL` |
| `food_id` | `UUID` | NÃO | - | `FOREIGN KEY REFERENCES foods(id) ON DELETE RESTRICT` |
| `food_name_snapshot` | `VARCHAR(255)` | NÃO | - | Cópia imutável do nome no momento do consumo |
| `weight_consumed_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (weight_consumed_g > 0)` - Peso real ingerido |
| `consumed_energy_kcal` | `NUMERIC(8,2)` | NÃO | - | `CHECK (consumed_energy_kcal >= 0)` |
| `consumed_protein_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (consumed_protein_g >= 0)` |
| `consumed_carbohydrate_g`| `NUMERIC(8,2)` | NÃO | - | `CHECK (consumed_carbohydrate_g >= 0)` |
| `consumed_lipid_g` | `NUMERIC(8,2)` | NÃO | - | `CHECK (consumed_lipid_g >= 0)` |
| `consumed_fiber_g` | `NUMERIC(8,2)` | NÃO | `0.00` | `CHECK (consumed_fiber_g >= 0)` |
| `consumed_micronutrients` | `JSONB` | NÃO | `'{}'::jsonb` | Snapshot dos micronutrientes consumidos |
| `is_off_diet` | `BOOLEAN` | NÃO | `false` | `false` = Na dieta; `true` = Fora da dieta (livre/cheat) |
| `logged_at` | `TIMESTAMPTZ` | NÃO | `CURRENT_TIMESTAMP` | Timestamp do momento em que foi registrado |

#### Índices de `daily_log_entries`:
- `CREATE INDEX idx_log_entries_daily_log_id ON daily_log_entries (daily_log_id);`
- `CREATE INDEX idx_log_entries_is_off_diet ON daily_log_entries (is_off_diet);`

---

## 🔄 3. Matriz de Compatibilidade e Mapeamento de Tipos

| PostgreSQL 16 | Java 21 / Hibernate 6 | TypeScript (Next.js) | Regra de Tratamento |
| :--- | :--- | :--- | :--- |
| `UUID` | `java.util.UUID` | `string` | UUID v4 canônico em caixa baixa |
| `NUMERIC(8,2)` | `java.math.BigDecimal` | `number` | Arredondamento com `RoundingMode.HALF_UP` |
| `VARCHAR(n)` | `java.lang.String` | `string` | Trim de espaços em branco nas bordas |
| `JSONB` | `MicronutrientsRecord` (Record Java) | `Micronutrients` (Interface) | `@JdbcTypeCode(SqlTypes.JSON)` |
| `DATE` | `java.time.LocalDate` | `string` | Padrão ISO-8601 (`YYYY-MM-DD`) |
| `TIME` | `java.time.LocalTime` | `string` | Padrão ISO-8601 (`HH:mm:ss`) |
| `TIMESTAMPTZ` | `java.time.OffsetDateTime` | `string` | Padrão ISO-8601 UTC com timezone |
| `BOOLEAN` | `java.lang.Boolean` | `boolean` | Primitivo booleano estrito |

---

## 🛡️ 4. Regras de Integridade Referencial (Foreign Keys)
1. **`foods` é protegido contra deleção acidental (`ON DELETE RESTRICT`):** Nenhum alimento que já conste em um template (`meal_items`) ou em um histórico (`daily_log_entries`) pode ser excluído fisicamente do banco de dados. Caso um alimento precise ser desativado, utiliza-se soft-delete na camada de negócio.
2. **Deleção em cascata para dependentes estruturais (`ON DELETE CASCADE`):** 
   - Ao excluir uma refeição (`meals`), todos os seus `meal_items` são excluídos automaticamente.
   - Ao excluir um dia (`daily_logs`), todas as suas entradas (`daily_log_entries`) são excluídas em lote.
3. **Desacoplamento de templates no histórico (`ON DELETE SET NULL`):**
   - Se uma refeição template (`meals`) for excluída, as entradas já consumidas (`daily_log_entries.meal_id`) são preservadas com `NULL`, mantendo intacto o snapshot calórico diário do usuário.

---

## ⚙️ 5. Protocolo de Migração e Baseline do Flyway (Spring Boot 3.3)

Como o schema PostgreSQL pode ser inicializado pelo container Docker (`/docker-entrypoint-initdb.d/01_init_schema.sql`) e populado antecipadamente pelo pipeline de ETL em Python (Etapa 2), o Flyway no Spring Boot **não deve tentar recriar tabelas existentes nem falhar por colisão de versão**.

### Propriedades Obrigatórias no `application.yml` (Etapa 3):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5433/precision_nutrition
    username: precision_user
    password: precision_pass
    driver-class-name: org.postgresql.Driver
  threads:
    virtual:
      enabled: true
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
    baseline-description: "Initial schema baseline from Docker/ETL"
    locations: classpath:db/migration
```

> [!NOTE]
> **Isolamento de Porta:** O container PostgreSQL 16 expõe no host a porta **5433** (`127.0.0.1:5433`), protegendo o backend de colisões acidentais com a porta padrão 5432.


> [!IMPORTANT]
> A propriedade `baseline-on-migrate: true` instrui o Flyway a reconhecer a existência prévia das tabelas criadas pelo script `V1__init_schema.sql` (executado pelo container Docker ou migração), registrando a versão 1 na tabela `flyway_schema_history` sem lançar exceções de integridade estrutural. Além disso, todas as cláusulas do `V1__init_schema.sql` utilizam `IF NOT EXISTS` e `DROP TRIGGER IF EXISTS` para idempotência absoluta.

