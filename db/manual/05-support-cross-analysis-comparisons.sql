-- Apply once after 04 with application writers stopped.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
LOCK TABLE public.analysis_runs, public.startup_conditions, public.property_analyses IN SHARE MODE;
ALTER TABLE public.comparison_ai_consultations
    ADD COLUMN comparison_key varchar(64),
    ADD COLUMN recommended_property_analysis_id bigint,
    ADD COLUMN risk_factors jsonb,
    ADD COLUMN positive_factors jsonb,
    ADD COLUMN recommended_contract_conditions jsonb,
    ADD COLUMN condition_differences text;

DO $$
DECLARE
    c record;
    item jsonb;
    source record;
    enriched jsonb;
    financial jsonb;
    items jsonb;
    ids bigint[];
    seen bigint[];
    candidate bigint;
    restored_key text;
BEGIN
    FOR c IN SELECT id, user_id, analysis_run_id, property_snapshots FROM public.comparison_ai_consultations LOOP
        IF jsonb_typeof(c.property_snapshots) IS DISTINCT FROM 'array'
            OR jsonb_array_length(c.property_snapshots) = 0 THEN
            RAISE EXCEPTION 'Invalid comparison snapshot array (%)', c.id;
        END IF;
        items := '[]';
        ids := ARRAY[]::bigint[];
        seen := ARRAY[]::bigint[];
        restored_key := NULL;
        FOR item IN SELECT value FROM jsonb_array_elements(c.property_snapshots) WITH ORDINALITY ORDER BY ordinality LOOP
            IF jsonb_typeof(item->'propertyId') IS DISTINCT FROM 'number'
                OR item ?| ARRAY['analysisRunId','propertyAnalysisId','conditionSnapshot','assumptionsSnapshot',
                    'analyzedAt','calculationVersion','dataCutoffAt']
                OR (item ? 'financialResult' AND jsonb_typeof(item->'financialResult') NOT IN ('object','null')) THEN
                RAISE EXCEPTION 'Unexpected legacy comparison snapshot (%)', c.id;
            END IF;
            candidate := (item->>'propertyId')::bigint;
            IF candidate = ANY(seen) THEN RAISE EXCEPTION 'Duplicate comparison candidate (%)', c.id; END IF;
            seen := array_append(seen,candidate);
            enriched := item || jsonb_build_object('analysisRunId',c.analysis_run_id);
            -- A scalar subquery rejects ambiguous matches. A missing source remains unknown.
            SELECT sc.user_id AS owner_id,r.completed_at,r.calculation_version,r.data_cutoff_at,r.condition_snapshot,r.assumptions_snapshot,
                   (SELECT p.id FROM public.property_analyses p WHERE p.analysis_run_id=r.id
                         AND p.candidate_property_id=candidate) AS result_id
            INTO source FROM public.analysis_runs r
            LEFT JOIN public.startup_conditions sc ON sc.id=r.startup_condition_id
            WHERE r.id=c.analysis_run_id;
            IF FOUND THEN
                IF source.owner_id IS NULL OR source.owner_id IS DISTINCT FROM c.user_id THEN
                    RAISE EXCEPTION 'Comparison source owner mismatch (comparison %)', c.id;
                END IF;
                enriched := enriched || jsonb_build_object('analyzedAt',source.completed_at,
                    'calculationVersion',source.calculation_version,'dataCutoffAt',source.data_cutoff_at,
                    'conditionSnapshot',source.condition_snapshot - 'userId','assumptionsSnapshot',source.assumptions_snapshot);
                IF source.result_id IS NOT NULL THEN
                    ids := array_append(ids,source.result_id);
                    SELECT jsonb_build_object('monthlyFixedCost',monthly_fixed_cost,'variableCostRate',variable_cost_rate,
                           'expectedMonthlySales',expected_monthly_sales,'monthlyOperatingProfit',monthly_operating_profit,
                           'breakEvenSales',break_even_sales,'breakEvenStatus',break_even_status,'initialRequiredCash',initial_required_cash,
                           'initialOperatingCash',initial_operating_cash,'safetyScore',safety_score,'safetyGrade',safety_grade,
                           'recommendationRank',recommendation_rank,'recommendationReason',recommendation_reason)
                    INTO financial FROM public.property_analyses WHERE id=source.result_id;
                    IF jsonb_typeof(item->'financialResult') IS DISTINCT FROM 'object' THEN
                        enriched := enriched || jsonb_build_object('financialResult',financial);
                    ELSE
                        enriched := enriched || jsonb_build_object('financialResult', financial || (item->'financialResult'));
                    END IF;
                    enriched := enriched || jsonb_build_object('propertyAnalysisId',source.result_id);
                END IF;
            END IF;
            items := items || jsonb_build_array(enriched);
        END LOOP;
        IF cardinality(ids) = jsonb_array_length(items) AND cardinality(ids) BETWEEN 2 AND 3 THEN
            SELECT string_agg(id::text, ':' ORDER BY id) INTO restored_key FROM unnest(ids) id;
        END IF;
        IF jsonb_array_length(items) <> jsonb_array_length(c.property_snapshots)
           OR EXISTS (SELECT FROM jsonb_array_elements(items) e WHERE e->'analysisRunId' IS DISTINCT FROM to_jsonb(c.analysis_run_id)) THEN
            RAISE EXCEPTION 'Incomplete provenance (%)', c.id;
        END IF;
        UPDATE public.comparison_ai_consultations SET property_snapshots=items, comparison_key=restored_key WHERE id=c.id;
    END LOOP;
END $$;

-- A key collision aborts the transaction, preserving both original rows and the old constraint.
ALTER TABLE public.comparison_ai_consultations ADD CONSTRAINT uq_comparison_user_key UNIQUE (user_id, comparison_key);
-- PostgreSQL drops the unique constraint belonging to this column together with the column.
ALTER TABLE public.comparison_ai_consultations DROP COLUMN analysis_run_id;
COMMIT;
