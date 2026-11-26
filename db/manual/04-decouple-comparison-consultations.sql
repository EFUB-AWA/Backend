-- Stop application writers before applying this once to the legacy public schema.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
LOCK TABLE public.analysis_runs, public.startup_conditions, public.property_analyses IN SHARE MODE;
ALTER TABLE public.comparison_ai_consultations ADD COLUMN user_id bigint;

UPDATE public.comparison_ai_consultations c
SET user_id = s.user_id
FROM public.analysis_runs r JOIN public.startup_conditions s ON s.id = r.startup_condition_id
WHERE r.id = c.analysis_run_id;

DO $$
DECLARE
    c record;
    snapshot_ids jsonb;
    result_ids jsonb;
    enriched jsonb;
    unique_ids integer;
    original_count bigint;
    updated_count bigint := 0;
    run_fk name;
BEGIN
    SELECT count(*) INTO original_count FROM public.comparison_ai_consultations;
    FOR c IN SELECT id, analysis_run_id, user_id, property_snapshots FROM public.comparison_ai_consultations LOOP
        IF c.user_id IS NULL OR jsonb_typeof(c.property_snapshots) IS DISTINCT FROM 'array' THEN
            RAISE EXCEPTION 'Missing comparison owner or snapshot array (comparison %)', c.id;
        END IF;
        SELECT jsonb_agg(item->'propertyId' ORDER BY item->'propertyId'), count(DISTINCT item->'propertyId')
        INTO snapshot_ids, unique_ids FROM jsonb_array_elements(c.property_snapshots) item;
        SELECT jsonb_agg(to_jsonb(candidate_property_id) ORDER BY candidate_property_id)
        INTO result_ids FROM public.property_analyses WHERE analysis_run_id = c.analysis_run_id;

        IF unique_ids = 0 OR unique_ids <> jsonb_array_length(c.property_snapshots)
                OR snapshot_ids IS DISTINCT FROM result_ids THEN
            RAISE EXCEPTION 'Duplicate, missing or unmatched property IDs (comparison %)', c.id;
        END IF;

        SELECT jsonb_agg(item || jsonb_build_object('financialResult', jsonb_build_object(
            'expectedMonthlySales', p.expected_monthly_sales, 'monthlyOperatingProfit', p.monthly_operating_profit,
            'breakEvenSales', p.break_even_sales, 'breakEvenStatus', p.break_even_status,
            'initialRequiredCash', p.initial_required_cash, 'initialOperatingCash', p.initial_operating_cash,
            'safetyScore', p.safety_score, 'safetyGrade', p.safety_grade,
            'recommendationRank', p.recommendation_rank, 'recommendationReason', p.recommendation_reason
        )) ORDER BY pos) INTO enriched
        FROM jsonb_array_elements(c.property_snapshots) WITH ORDINALITY AS snapshot(item, pos)
        JOIN public.property_analyses p ON p.analysis_run_id = c.analysis_run_id
            AND to_jsonb(p.candidate_property_id) = item->'propertyId'
        WHERE NOT (item ? 'financialResult')
            AND p.expected_monthly_sales IS NOT NULL AND p.monthly_operating_profit IS NOT NULL
            AND p.break_even_status IS NOT NULL AND (p.break_even_sales IS NOT NULL OR p.break_even_status = 'UNREACHABLE')
            AND p.initial_required_cash IS NOT NULL AND p.initial_operating_cash IS NOT NULL
            AND p.safety_score IS NOT NULL AND p.safety_grade IS NOT NULL
            AND p.recommendation_rank IS NOT NULL AND p.recommendation_reason IS NOT NULL;
        IF jsonb_array_length(enriched) IS DISTINCT FROM unique_ids THEN
            RAISE EXCEPTION 'Incomplete or conflicting financial result (comparison %)', c.id;
        END IF;
        UPDATE public.comparison_ai_consultations SET property_snapshots = enriched WHERE id = c.id;
        IF FOUND THEN updated_count := updated_count + 1; END IF;
    END LOOP;
    IF updated_count <> original_count THEN
        RAISE EXCEPTION 'Comparison row count mismatch';
    END IF;

    ALTER TABLE public.comparison_ai_consultations ALTER COLUMN user_id SET NOT NULL,
        ADD CONSTRAINT fk_comparison_user FOREIGN KEY (user_id) REFERENCES public.users(id);
    CREATE INDEX idx_comparison_saved_user_id ON public.comparison_ai_consultations (user_id, id DESC) WHERE is_saved;

    -- Hibernate's generated FK name can differ between installations.
    SELECT k.conname INTO STRICT run_fk FROM pg_constraint k
    WHERE k.contype = 'f' AND k.conrelid = 'public.comparison_ai_consultations'::regclass
        AND k.confrelid = 'public.analysis_runs'::regclass
        AND k.conkey = ARRAY[(SELECT attnum FROM pg_attribute
            WHERE attrelid = k.conrelid AND attname = 'analysis_run_id')];
    EXECUTE format('ALTER TABLE public.comparison_ai_consultations DROP CONSTRAINT %I', run_fk);
END $$;
COMMIT;
