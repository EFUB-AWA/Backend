-- 저장된 컨설팅은 만료 시각이 지나도 삭제되지 않는지 확인용 (저장됨 + 만료)
WITH runs (id, startup_condition_id, title, status, candidate_property_ids, condition_snapshot) AS (
    VALUES
        (9014, 9001, '저장 후 만료 시각 지남', 'COMPLETED', '[9001, 9002]'::jsonb,
         '{"title":"서대문구 카페 창업"}'::jsonb)
)
INSERT INTO analysis_runs
(id, startup_condition_id, title, status, candidate_property_ids, property_snapshots, condition_snapshot, created_at, updated_at)
SELECT id, startup_condition_id, title, status, candidate_property_ids,
       pg_temp.fixture_property_snapshots(candidate_property_ids), condition_snapshot, now(), now()
FROM runs;

INSERT INTO comparison_ai_consultations (id, user_id, property_snapshots, status, summary, description, pros_and_cons,
                                         model_name, prompt_version, is_saved, completed_at, saved_at, expires_at, created_at, updated_at)
VALUES
    (9012, 9001, '[{"analysisRunId":9014,"propertyId":9001,"name":"이대 정문 1층"},{"analysisRunId":9014,"propertyId":9002,"name":"신촌역 앞"}]'::jsonb,
     'COMPLETED', '저장 후 만료 요약', '저장 후 만료 설명', '저장 후 만료 장단점',
     'gpt-4o-mini', 'v1', true,
     now() - interval '3 day',   -- completed_at
     now() - interval '2 day',   -- saved_at (만료 전에 저장)
     now() - interval '1 day',   -- expires_at (지금은 지남)
     now() - interval '3 day', now());
