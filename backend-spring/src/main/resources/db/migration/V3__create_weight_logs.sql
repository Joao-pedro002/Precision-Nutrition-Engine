-- ============================================================================
-- PRECISION NUTRITION ENGINE - WEIGHT LOGS TABLE (ETAPA 11)
-- ============================================================================

CREATE TABLE IF NOT EXISTS weight_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    log_date DATE NOT NULL,
    weight_kg NUMERIC(6,2) NOT NULL CHECK (weight_kg > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_weight_logs_user_date UNIQUE (user_id, log_date)
);

CREATE INDEX IF NOT EXISTS idx_weight_logs_user_date ON weight_logs (user_id, log_date DESC);

DROP TRIGGER IF EXISTS trg_weight_logs_set_updated_at ON weight_logs;
CREATE TRIGGER trg_weight_logs_set_updated_at
BEFORE UPDATE ON weight_logs
FOR EACH ROW EXECUTE FUNCTION set_updated_at_timestamp();
