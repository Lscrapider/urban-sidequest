CREATE TABLE route_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    request_id UUID NOT NULL REFERENCES route_generation_history(request_id),
    route_code VARCHAR(16) NOT NULL,
    execution_status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_route_execution_completed_at CHECK (
        execution_status <> 'COMPLETED' OR completed_at IS NOT NULL
    )
);

CREATE INDEX idx_route_execution_user_updated_at
    ON route_execution (user_id, updated_at DESC);

CREATE INDEX idx_route_execution_request_updated_at
    ON route_execution (request_id, updated_at DESC);

CREATE UNIQUE INDEX uk_route_execution_user_in_progress
    ON route_execution (user_id)
    WHERE execution_status = 'IN_PROGRESS';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'route_generation_history'
          AND column_name = 'active_route_code'
    ) THEN
        EXECUTE $migration$
            INSERT INTO route_execution (
                user_id,
                request_id,
                route_code,
                execution_status,
                started_at,
                completed_at,
                created_at,
                updated_at
            )
            SELECT
                user_id,
                request_id,
                active_route_code,
                execution_status,
                created_at,
                CASE WHEN execution_status = 'COMPLETED' THEN updated_at ELSE NULL END,
                created_at,
                updated_at
            FROM route_generation_history
            WHERE active_route_code IS NOT NULL
              AND execution_status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')
        $migration$;
    END IF;
END $$;

DROP INDEX IF EXISTS uk_route_generation_history_user_in_progress;

ALTER TABLE route_generation_history
    DROP CONSTRAINT IF EXISTS ck_route_generation_history_active_status,
    DROP COLUMN IF EXISTS active_route_code,
    DROP COLUMN IF EXISTS execution_status;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_execution TO urban_sidequest;
    END IF;
END $$;
