-- The common fixture supplies two owners, four analyses of properties and their consultations.
UPDATE analysis_runs SET condition_snapshot = condition_snapshot ||
    '{"industryId":1,"preferredRegionCode":"11410","loanAmount":0}'::jsonb WHERE id=9001;
UPDATE property_analyses SET expected_monthly_sales=100, monthly_fixed_cost=10, variable_cost_rate=0.2,
    monthly_operating_profit=70, break_even_sales=20, break_even_status='ACHIEVED', initial_required_cash=40,
    initial_operating_cash=30, safety_score=80, safety_grade='A', recommendation_rank=1, recommendation_reason='fixture';
UPDATE property_ai_consultations SET status='FAILED' WHERE id=9002;

-- A second completed run owned by the same user uses exactly the same candidate properties.
INSERT INTO analysis_runs (id,startup_condition_id,status,candidate_property_ids,condition_snapshot,property_snapshots,
    created_at,updated_at) SELECT 9010,startup_condition_id,status,candidate_property_ids,condition_snapshot,
    property_snapshots,now(),now() FROM analysis_runs WHERE id=9001;
INSERT INTO property_analyses (id,analysis_run_id,candidate_property_id,property_snapshot,safety_grade,created_at,updated_at)
    SELECT id+100,9010,candidate_property_id,property_snapshot,'B',now(),now()
    FROM property_analyses WHERE analysis_run_id=9001;
INSERT INTO property_ai_consultations (id,property_analysis_id,status,created_at,updated_at)
    VALUES (9101,9101,'COMPLETED',now(),now());

INSERT INTO comparison_ai_consultations (id,analysis_run_id,user_id,property_snapshots,is_saved,status,summary,
    description,pros_and_cons,saved_at,expires_at,created_at,updated_at)
    VALUES (1,9001,9001,'[{"propertyId":9001,"name":"historic","financialResult":{"recommendationRank":1}}]',
    true,'COMPLETED','summary','description','pros',now(),null,now(),now()),
    (2,9010,9001,'[{"propertyId":9002,"name":"temporary"}]',false,'COMPLETED','temporary',null,null,null,
    now()+interval '1 day',now(),now());
INSERT INTO external_data_snapshots (id,source_name,dataset_code,reference_year,fetched_at,processing_version,
    data_payload,created_at,updated_at) VALUES (7,'test','shared',2025,now(),'v1','{}',now(),now());
INSERT INTO cost_benchmarks (id,kosis_industry_code,kosis_industry_name,region_code,reference_year,
    monthly_labor_cost,cost_of_sales_rate,monthly_other_operating_cost,created_at,updated_at)
    VALUES (7,'test','shared','test',2025,100,30,50,now(),now());
INSERT INTO cost_benchmark_sources (cost_benchmark_id,external_data_snapshot_id) VALUES (7,7);
INSERT INTO industry_cost_benchmark_mappings (industry_id,cost_benchmark_id) VALUES (1,7);
