-- ============================================================================
-- PRECISION NUTRITION ENGINE - DATABASE SCHEMA INITIALIZATION
-- Database: PostgreSQL 16
-- Extensions: uuid-ossp, pg_trgm, unaccent
-- Standard: ISO SQL, NUMERIC(8,2) for exact macros, JSONB for micronutrients
-- Idempotency: All statements use IF NOT EXISTS / DROP TRIGGER IF EXISTS
-- ============================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- 2. HELPER FUNCTIONS
-- Standard PostgreSQL 'unaccent' is STABLE.
-- An IMMUTABLE wrapper is mathematically required to allow expression-based GIN indexing.
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text AS $$
    SELECT public.unaccent('public.unaccent', $1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- Generic trigger function for automated updated_at timestamps
CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 3. TABLE DEFINITIONS
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 3.1. FOODS (Catálogo de Alimentos Normalizados para Base de 100g)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS foods (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN ('TACO', 'USDA', 'CUSTOM')),
    external_id VARCHAR(100),
    category VARCHAR(100),
    dominant_macro VARCHAR(20) NOT NULL CHECK (dominant_macro IN ('PROTEIN', 'CARB', 'FAT', 'BALANCED')),
    serving_size_g NUMERIC(8,2) NOT NULL DEFAULT 100.00 CHECK (serving_size_g > 0),
    energy_kcal NUMERIC(8,2) NOT NULL CHECK (energy_kcal >= 0),
    protein_g NUMERIC(8,2) NOT NULL CHECK (protein_g >= 0),
    carbohydrate_g NUMERIC(8,2) NOT NULL CHECK (carbohydrate_g >= 0),
    lipid_g NUMERIC(8,2) NOT NULL CHECK (lipid_g >= 0),
    fiber_g NUMERIC(8,2) NOT NULL DEFAULT 0.00 CHECK (fiber_g >= 0),
    micronutrients JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for foods
CREATE INDEX IF NOT EXISTS idx_foods_micronutrients_gin ON foods USING GIN (micronutrients jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_foods_name_trgm ON foods USING GIN (immutable_unaccent(name) gin_trgm_ops);
CREATE UNIQUE INDEX IF NOT EXISTS uq_foods_source_external_id ON foods (source, external_id);
CREATE INDEX IF NOT EXISTS idx_foods_dominant_macro ON foods (dominant_macro);
CREATE INDEX IF NOT EXISTS idx_foods_source ON foods (source);

DROP TRIGGER IF EXISTS trg_foods_set_updated_at ON foods;
CREATE TRIGGER trg_foods_set_updated_at
BEFORE UPDATE ON foods
FOR EACH ROW EXECUTE FUNCTION set_updated_at_timestamp();

-- ----------------------------------------------------------------------------
-- 3.2. DAILY_TARGETS (Metas Diárias, Zonas Calóricas e Balanço Nutricional)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_targets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT false,
    target_kcal_min NUMERIC(8,2) NOT NULL CHECK (target_kcal_min >= 0),
    target_kcal_max NUMERIC(8,2) NOT NULL CHECK (target_kcal_max >= 0),
    maintenance_kcal_ceiling NUMERIC(8,2) NOT NULL CHECK (maintenance_kcal_ceiling >= 0),
    target_protein_g NUMERIC(8,2) NOT NULL CHECK (target_protein_g >= 0),
    target_carbohydrate_g NUMERIC(8,2) NOT NULL CHECK (target_carbohydrate_g >= 0),
    target_lipid_g NUMERIC(8,2) NOT NULL CHECK (target_lipid_g >= 0),
    target_fiber_g NUMERIC(8,2) NOT NULL DEFAULT 30.00 CHECK (target_fiber_g >= 0),
    target_water_ml NUMERIC(8,2) NOT NULL DEFAULT 3500.00 CHECK (target_water_ml >= 0),
    target_micronutrients JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_kcal_zones CHECK (target_kcal_min <= target_kcal_max AND target_kcal_max <= maintenance_kcal_ceiling)
);

-- Indexes for daily_targets
-- Enforces strictly at most ONE active protocol per user at the database layer (ADR-006)
CREATE UNIQUE INDEX IF NOT EXISTS uq_daily_targets_single_active ON daily_targets (user_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_daily_targets_user_id ON daily_targets (user_id);

DROP TRIGGER IF EXISTS trg_daily_targets_set_updated_at ON daily_targets;
CREATE TRIGGER trg_daily_targets_set_updated_at
BEFORE UPDATE ON daily_targets
FOR EACH ROW EXECUTE FUNCTION set_updated_at_timestamp();

-- ----------------------------------------------------------------------------
-- 3.3. MEALS (Templates de Refeições Prescritas)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS meals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    order_index INT NOT NULL DEFAULT 0,
    target_time TIME,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_meals_user_order ON meals (user_id, order_index);

DROP TRIGGER IF EXISTS trg_meals_set_updated_at ON meals;
CREATE TRIGGER trg_meals_set_updated_at
BEFORE UPDATE ON meals
FOR EACH ROW EXECUTE FUNCTION set_updated_at_timestamp();

-- ----------------------------------------------------------------------------
-- 3.4. MEAL_ITEMS (Itens de Cada Refeição com Suporte a Multi-Opção)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS meal_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    food_id UUID NOT NULL REFERENCES foods(id) ON DELETE RESTRICT,
    option_number INT NOT NULL DEFAULT 1 CHECK (option_number >= 1),
    weight_g NUMERIC(8,2) NOT NULL CHECK (weight_g > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_meal_items_meal_option ON meal_items (meal_id, option_number);
CREATE INDEX IF NOT EXISTS idx_meal_items_food_id ON meal_items (food_id);

-- ----------------------------------------------------------------------------
-- 3.5. DAILY_LOGS (Registro Diário de Execução e Hidratação)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    date DATE NOT NULL,
    water_consumed_ml NUMERIC(8,2) NOT NULL DEFAULT 0.00 CHECK (water_consumed_ml >= 0),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_daily_logs_user_date ON daily_logs (user_id, date);
CREATE INDEX IF NOT EXISTS idx_daily_logs_user_id ON daily_logs (user_id);

DROP TRIGGER IF EXISTS trg_daily_logs_set_updated_at ON daily_logs;
CREATE TRIGGER trg_daily_logs_set_updated_at
BEFORE UPDATE ON daily_logs
FOR EACH ROW EXECUTE FUNCTION set_updated_at_timestamp();

-- ----------------------------------------------------------------------------
-- 3.6. DAILY_LOG_ENTRIES (Snapshot Imutável dos Alimentos Consumidos)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_log_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    daily_log_id UUID NOT NULL REFERENCES daily_logs(id) ON DELETE CASCADE,
    meal_id UUID REFERENCES meals(id) ON DELETE SET NULL,
    food_id UUID NOT NULL REFERENCES foods(id) ON DELETE RESTRICT,
    food_name_snapshot VARCHAR(255) NOT NULL,
    weight_consumed_g NUMERIC(8,2) NOT NULL CHECK (weight_consumed_g > 0),
    consumed_energy_kcal NUMERIC(8,2) NOT NULL CHECK (consumed_energy_kcal >= 0),
    consumed_protein_g NUMERIC(8,2) NOT NULL CHECK (consumed_protein_g >= 0),
    consumed_carbohydrate_g NUMERIC(8,2) NOT NULL CHECK (consumed_carbohydrate_g >= 0),
    consumed_lipid_g NUMERIC(8,2) NOT NULL CHECK (consumed_lipid_g >= 0),
    consumed_fiber_g NUMERIC(8,2) NOT NULL DEFAULT 0.00 CHECK (consumed_fiber_g >= 0),
    consumed_micronutrients JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_off_diet BOOLEAN NOT NULL DEFAULT false,
    logged_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_log_entries_daily_log_id ON daily_log_entries (daily_log_id);
CREATE INDEX IF NOT EXISTS idx_log_entries_meal_id ON daily_log_entries (meal_id);
CREATE INDEX IF NOT EXISTS idx_log_entries_food_id ON daily_log_entries (food_id);
CREATE INDEX IF NOT EXISTS idx_log_entries_is_off_diet ON daily_log_entries (is_off_diet);
