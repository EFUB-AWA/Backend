-- PostgreSQL 17, legacy local schema. See README.md before running.
BEGIN;
LOCK TABLE public.startup_conditions, public.candidate_properties
    IN ACCESS EXCLUSIVE MODE NOWAIT;

DO $$
BEGIN
    IF current_database() <> 'irion_dev' OR current_schema() IS DISTINCT FROM 'public' THEN
        RAISE EXCEPTION 'Expected irion_dev/public';
    END IF;
    IF EXISTS (SELECT FROM public.startup_conditions) OR EXISTS (SELECT FROM public.candidate_properties) THEN
        RAISE EXCEPTION 'Existing input data requires a separate migration';
    END IF;
    IF (to_regclass('public.analysis_runs') IS NULL) <> (to_regclass('public.property_analyses') IS NULL) THEN
        RAISE EXCEPTION 'Partial analysis schema requires review';
    END IF;
    IF to_regclass('public.analysis_runs') IS NOT NULL THEN
        LOCK TABLE public.analysis_runs, public.property_analyses IN ACCESS EXCLUSIVE MODE NOWAIT;
        IF EXISTS (SELECT FROM public.analysis_runs) OR EXISTS (SELECT FROM public.property_analyses) THEN
            RAISE EXCEPTION 'Existing analysis data requires review';
        END IF;
    END IF;
END $$;

ALTER TABLE public.startup_conditions
    ADD COLUMN loan_amount bigint NOT NULL,
    ADD COLUMN monthly_labor_cost bigint NOT NULL,
    ADD COLUMN cost_of_sales_rate numeric(5,2) NOT NULL,
    ADD COLUMN monthly_other_operating_cost bigint NOT NULL,
    ADD CONSTRAINT uq_startup_conditions_user_id UNIQUE (user_id),
    DROP COLUMN planned_opening_month;

ALTER TABLE public.candidate_properties
    ADD COLUMN commercial_area_code varchar(20),
    DROP CONSTRAINT fk8onr25j9d3lrd72bant6c8kyr,
    DROP COLUMN commercial_area_id,
    DROP CONSTRAINT candidate_properties_mapping_status_check;

ALTER TABLE public.candidate_properties
    ADD CONSTRAINT candidate_properties_mapping_status_check CHECK (mapping_status IN ('MAPPED', 'FAILED'));
COMMIT;
