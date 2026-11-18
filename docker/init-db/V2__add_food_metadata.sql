-- ============================================================================
-- V2__add_food_metadata.sql
-- Épica 1: Adicionar target_source em daily_targets
-- Épica 2: Adicionar popularity_score e food_group em foods, criar food_portions
-- ============================================================================

-- 1. DAILY_TARGETS: enum target_source
ALTER TABLE daily_targets 
ADD COLUMN IF NOT EXISTS target_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL' 
CHECK (target_source IN ('CALCULATED', 'MANUAL'));

-- 2. FOODS: popularity_score e food_group
ALTER TABLE foods 
ADD COLUMN IF NOT EXISTS popularity_score INT NOT NULL DEFAULT 10;

ALTER TABLE foods 
ADD COLUMN IF NOT EXISTS food_group VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_foods_popularity ON foods (popularity_score DESC);
CREATE INDEX IF NOT EXISTS idx_foods_food_group ON foods (food_group);

-- 3. FOOD_PORTIONS (Medidas Caseiras Culinárias)
CREATE TABLE IF NOT EXISTS food_portions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    food_id UUID NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    portion_name VARCHAR(100) NOT NULL,
    weight_g NUMERIC(8,2) NOT NULL CHECK (weight_g > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_food_portions_food_id ON food_portions (food_id);

-- 4. ATRIBUIÇÃO DE FOOD_GROUP AOS ALIMENTOS DA TACO / USDA
UPDATE foods SET food_group = 'PROTEINA_ANIMAL' WHERE LOWER(name) LIKE '%frango%' OR LOWER(name) LIKE '%carne%' OR LOWER(name) LIKE '%ovo%' OR LOWER(name) LIKE '%peixe%' OR LOWER(name) LIKE '%tilápia%' OR LOWER(name) LIKE '%patinho%' OR LOWER(name) LIKE '%atum%';
UPDATE foods SET food_group = 'CEREAL_TUBERCULO' WHERE LOWER(name) LIKE '%arroz%' OR LOWER(name) LIKE '%pão%' OR LOWER(name) LIKE '%batata%' OR LOWER(name) LIKE '%mandioca%' OR LOWER(name) LIKE '%tapioca%' OR LOWER(name) LIKE '%aveia%' OR LOWER(name) LIKE '%macarrão%';
UPDATE foods SET food_group = 'LEGUMINOSA' WHERE LOWER(name) LIKE '%feijão%' OR LOWER(name) LIKE '%lentilha%' OR LOWER(name) LIKE '%grão-de-bico%' OR LOWER(name) LIKE '%soja%' OR LOWER(name) LIKE '%ervilha%';
UPDATE foods SET food_group = 'FRUTA' WHERE LOWER(name) LIKE '%banana%' OR LOWER(name) LIKE '%maçã%' OR LOWER(name) LIKE '%laranja%' OR LOWER(name) LIKE '%abacaxi%' OR LOWER(name) LIKE '%manga%' OR LOWER(name) LIKE '%mamão%' OR LOWER(name) LIKE '%uva%' OR LOWER(name) LIKE '%melancia%' OR LOWER(name) LIKE '%morango%';
UPDATE foods SET food_group = 'LIQUIDO' WHERE LOWER(name) LIKE '%leite%' OR LOWER(name) LIKE '%suco%' OR LOWER(name) LIKE '%café%' OR LOWER(name) LIKE '%chá%' OR LOWER(name) LIKE '%água%';
UPDATE foods SET food_group = 'OLEO_GORDURA' WHERE LOWER(name) LIKE '%azeite%' OR LOWER(name) LIKE '%óleo%' OR LOWER(name) LIKE '%manteiga%' OR LOWER(name) LIKE '%castanha%' OR LOWER(name) LIKE '%amendoim%';
UPDATE foods SET food_group = 'LATICINIO' WHERE (LOWER(name) LIKE '%queijo%' OR LOWER(name) LIKE '%iogurte%' OR LOWER(name) LIKE '%requeijão%') AND food_group IS NULL;
UPDATE foods SET food_group = 'VEGETAL' WHERE (LOWER(name) LIKE '%alface%' OR LOWER(name) LIKE '%brócolis%' OR LOWER(name) LIKE '%cenoura%' OR LOWER(name) LIKE '%tomate%' OR LOWER(name) LIKE '%espinafre%') AND food_group IS NULL;

-- 5. ATRIBUIÇÃO DE POPULARIDADE (Score 1000 para itens básicos)
UPDATE foods SET popularity_score = 1000 WHERE LOWER(name) LIKE '%ovo, de galinha, inteiro, cozido%' OR LOWER(name) LIKE '%ovo, de galinha, clara, cozida%';
UPDATE foods SET popularity_score = 1000 WHERE LOWER(name) LIKE '%arroz, tipo 1, cozido%' OR LOWER(name) LIKE '%arroz, integral, cozido%';
UPDATE foods SET popularity_score = 1000 WHERE LOWER(name) LIKE '%pão, francês%' OR LOWER(name) LIKE '%pão, de forma, tradicional%';
UPDATE foods SET popularity_score = 1000 WHERE LOWER(name) LIKE '%frango, peito, sem pele, grelhado%' OR LOWER(name) LIKE '%frango, peito, sem pele, cozido%';
UPDATE foods SET popularity_score = 1000 WHERE LOWER(name) LIKE '%leite, de vaca, pasteurizado, desnatado%' OR LOWER(name) LIKE '%leite, de vaca, integral%';
UPDATE foods SET popularity_score = 1000 WHERE LOWER(name) LIKE '%tapioca%';

-- Alimentos secundários populares (Score 500)
UPDATE foods SET popularity_score = 500 WHERE LOWER(name) LIKE '%feijão, carioca, cozido%' OR LOWER(name) LIKE '%batata, doce, cozida%' OR LOWER(name) LIKE '%banana, prata, crua%' OR LOWER(name) LIKE '%azeite, de oliva, extravirgem%' OR LOWER(name) LIKE '%aveia, flocos, crua%';

-- 6. POPULAR MEDIDAS CASEIRAS (food_portions) PARA ITENS BÁSICOS
INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Ovo Médio (unidade)', 50.00
FROM foods WHERE LOWER(name) LIKE '%ovo, de galinha, inteiro, cozido%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Clara de Ovo (unidade)', 30.00
FROM foods WHERE LOWER(name) LIKE '%ovo, de galinha, clara, cozida%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Pão Francês (unidade)', 50.00
FROM foods WHERE LOWER(name) LIKE '%pão, francês%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Copo Americano / Requeijão (200ml)', 200.00
FROM foods WHERE LOWER(name) LIKE '%leite, de vaca, pasteurizado, desnatado%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Colher de Sopa Cheia', 25.00
FROM foods WHERE LOWER(name) LIKE '%arroz, tipo 1, cozido%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Concha Média', 130.00
FROM foods WHERE LOWER(name) LIKE '%feijão, carioca, cozido%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Banana Média (unidade)', 100.00
FROM foods WHERE LOWER(name) LIKE '%banana, prata, crua%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Filé Médio Grelhado', 100.00
FROM foods WHERE LOWER(name) LIKE '%frango, peito, sem pele, grelhado%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Colher de Sopa (13ml)', 13.00
FROM foods WHERE LOWER(name) LIKE '%azeite, de oliva, extravirgem%'
LIMIT 1;

INSERT INTO food_portions (food_id, portion_name, weight_g)
SELECT id, '1 Colher de Sopa Rasa', 15.00
FROM foods WHERE LOWER(name) LIKE '%aveia, flocos, crua%'
LIMIT 1;
