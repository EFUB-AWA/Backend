-- Only for the empty, pre-snapshot analysis tables in the local development DB.
BEGIN;
DO $$
BEGIN
    IF current_database() <> 'irion_dev' OR current_schema() IS DISTINCT FROM 'public' THEN
        RAISE EXCEPTION 'Expected irion_dev/public';
    END IF;
    LOCK TABLE public.analysis_runs IN ACCESS EXCLUSIVE MODE;
    IF NOT EXISTS (
        SELECT FROM pg_class WHERE oid = 'public.analysis_runs'::regclass AND relkind = 'r'
    ) OR to_regclass('public.property_analyses') IS NULL THEN
        RAISE EXCEPTION 'Expected existing analysis tables from migration 02';
    END IF;

    -- Compare the target columns (name, SQL type, NOT NULL) with the original 02.
    IF EXISTS (
        SELECT FROM (VALUES
            ('id', 'bigint', true),
            ('startup_condition_id', 'bigint', true),
            ('title', 'character varying(255)', false),
            ('status', 'character varying(20)', true),
            ('candidate_property_ids', 'jsonb', true),
            ('condition_snapshot', 'jsonb', true),
            ('assumptions_snapshot', 'jsonb', false),
            ('calculation_version', 'character varying(255)', false),
            ('data_cutoff_at', 'timestamp(6) without time zone', false),
            ('horizon_months', 'integer', false),
            ('completed_at', 'timestamp(6) without time zone', false),
            ('created_at', 'timestamp(6) without time zone', true),
            ('updated_at', 'timestamp(6) without time zone', true)
        ) AS expected(col, typ, required)
        FULL JOIN (
            SELECT attname::text AS col, format_type(atttypid, atttypmod) AS typ, attnotnull AS required
            FROM pg_attribute
            WHERE attrelid = 'public.analysis_runs'::regclass AND attnum > 0 AND NOT attisdropped
        ) AS actual USING (col, typ, required)
        WHERE expected.col IS NULL OR actual.col IS NULL
    ) THEN
        RAISE EXCEPTION 'Unexpected analysis_runs columns or property_snapshots already present';
    END IF;
    IF EXISTS (SELECT FROM public.analysis_runs) THEN
        RAISE EXCEPTION 'Existing runs require a separate data migration';
    END IF;
END $$;

ALTER TABLE public.analysis_runs
    ADD COLUMN property_snapshots jsonb NOT NULL,
    ADD CONSTRAINT ck_analysis_runs_property_snapshots CHECK (
        jsonb_typeof(property_snapshots) = 'array' AND property_snapshots <> '[]'::jsonb
    );
COMMIT;
