-- Apply after 05 with old generation workers drained.
BEGIN;
ALTER TABLE public.comparison_ai_consultations
    ADD COLUMN attempt_no bigint NOT NULL DEFAULT 1,
    ADD COLUMN attempt_started_at timestamp(6) without time zone;
UPDATE public.comparison_ai_consultations SET attempt_started_at = created_at;
ALTER TABLE public.comparison_ai_consultations
    ALTER COLUMN attempt_started_at SET NOT NULL,
    ALTER COLUMN attempt_started_at SET DEFAULT CURRENT_TIMESTAMP;
COMMIT;
