ALTER TABLE route_generation_history
    ADD COLUMN generation_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    ADD COLUMN generation_stage VARCHAR(64);

UPDATE route_generation_history
SET generation_status = 'SUCCESS',
    generation_stage = 'completed'
WHERE generation_stage IS NULL;
