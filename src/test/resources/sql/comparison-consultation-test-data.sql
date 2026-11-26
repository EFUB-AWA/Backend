-- 비교 AI 컨설팅 테스트 데이터
-- 선행: property-consultation-test-data.sql (회원 9001·9002, 창업 조건 9001·9002, 매물 9001~9004)

-- 분석 실행 (비교 컨설팅과 1:1이라 컨설팅마다 하나씩)
WITH runs (id, startup_condition_id, title, status, candidate_property_ids, condition_snapshot) AS (
    VALUES
        (9003, 9001, '서대문구 비교 1', 'COMPLETED', '[9001, 9002, 9003]'::jsonb,
         '{"title":"서대문구 카페 창업"}'::jsonb),
        (9004, 9001, '서대문구 비교 2', 'COMPLETED', '[9001, 9002]'::jsonb,
         '{"title":"서대문구 카페 창업"}'::jsonb),
        (9005, 9001, '서대문구 비교 3', 'COMPLETED', '[9002, 9003]'::jsonb,
         '{"title":"서대문구 카페 창업"}'::jsonb),
        (9006, 9001, '서대문구 비교 4', 'COMPLETED', '[9001, 9003]'::jsonb,
         '{"title":"서대문구 카페 창업"}'::jsonb),
        (9007, 9001, '서대문구 비교 5', 'COMPLETED', '[9001, 9002, 9003]'::jsonb,
         '{"title":"서대문구 카페 창업"}'::jsonb),
        (9008, 9002, '마포구 비교',     'COMPLETED', '[9004]'::jsonb,
         '{"title":"마포구 카페 창업"}'::jsonb)
)
INSERT INTO analysis_runs
(id, startup_condition_id, title, status, candidate_property_ids, property_snapshots, condition_snapshot, created_at, updated_at)
SELECT id, startup_condition_id, title, status, candidate_property_ids,
       pg_temp.fixture_property_snapshots(candidate_property_ids), condition_snapshot, now(), now()
FROM runs;

-- 비교 AI 컨설팅
INSERT INTO comparison_ai_consultations
(id, analysis_run_id, user_id, property_snapshots, status, summary, description, pros_and_cons,
 model_name, prompt_version, is_saved, completed_at, saved_at, expires_at, created_at, updated_at)
VALUES
    -- A: COMPLETED + 저장함 → 200, 전체 내용
    (9001, 9003, 9001,
     '[{"propertyId":9001,"name":"이대 정문 1층","address":"서울특별시 서대문구 이화여대길 52","addressDetail":"101호","areaM2":66.12,"deposit":30000000,"monthlyRent":2500000,"keyMoney":40000000,"monthlyManagementFee":200000,"financialResult":{"expectedMonthlySales":9000000,"monthlyOperatingProfit":2000000,"breakEvenSales":null,"breakEvenStatus":"UNREACHABLE","initialRequiredCash":75000000,"initialOperatingCash":20000000,"safetyScore":72.5,"safetyGrade":"B","recommendationRank":2,"recommendationReason":"fixture reason"}},
       {"propertyId":9002,"name":"신촌역 앞","address":"서울특별시 서대문구 신촌역로 30","addressDetail":null,"areaM2":49.50,"deposit":20000000,"monthlyRent":1800000,"keyMoney":25000000,"monthlyManagementFee":150000},
       {"propertyId":9003,"name":"연대 동문","address":"서울특별시 서대문구 연세로 50","addressDetail":null,"areaM2":33.00,"deposit":10000000,"monthlyRent":1200000,"keyMoney":0,"monthlyManagementFee":100000}]'::jsonb,
     'COMPLETED',
     '세 매물 중 신촌역 앞 매물을 가장 추천합니다.',
     '유동인구 대비 월세 부담이 가장 낮고, 손익분기 매출 여유가 큽니다.',
     '이대 정문 1층은 매출 잠재력이 가장 높지만 월세와 권리금 부담이 큽니다. 연대 동문은 비용은 낮지만 면적이 작습니다.',
     'gpt-4o-mini', 'v1', true, now() - interval '2 hour', now() - interval '1 hour', NULL,
     now() - interval '3 hour', now()),

    -- A: COMPLETED + 저장 안 함 + 만료 전 → 200, savedAt null
    (9002, 9004, 9001,
     '[{"propertyId":9001,"name":"이대 정문 1층","deposit":30000000,"monthlyRent":2500000},
       {"propertyId":9002,"name":"신촌역 앞","deposit":20000000,"monthlyRent":1800000}]'::jsonb,
     'COMPLETED',
     '두 매물 중 신촌역 앞 매물을 추천합니다.',
     '고정비 차이가 월 70만 원 이상입니다.',
     '이대 정문 1층은 입지가 좋지만 고정비가 높습니다.',
     'gpt-4o-mini', 'v1', false, now() - interval '30 minute', NULL, now() + interval '1 day',
     now() - interval '1 hour', now()),

    -- A: COMPLETED + 저장 안 함 + 만료 → 404 CONSULTATION_EXPIRED
    (9003, 9005, 9001,
     '[{"propertyId":9002,"name":"신촌역 앞"},
       {"propertyId":9003,"name":"연대 동문"}]'::jsonb,
     'COMPLETED',
     '만료된 컨설팅 요약', '만료된 컨설팅 설명', '만료된 컨설팅 장단점',
     'gpt-4o-mini', 'v1', false, now() - interval '2 day', NULL, now() - interval '1 hour',
     now() - interval '2 day', now()),

    -- A: PENDING → 200, 내용 null, properties는 있음
    (9004, 9006, 9001,
     '[{"propertyId":9001,"name":"이대 정문 1층"},
       {"propertyId":9003,"name":"연대 동문"}]'::jsonb,
     'PENDING',
     NULL, NULL, NULL,
     'gpt-4o-mini', 'v1', false, NULL, NULL, now() + interval '1 day',
     now(), now()),

    -- A: FAILED (내용 일부 남아 있음) → 200, 내용 null, properties는 있음
    (9005, 9007, 9001,
     '[{"propertyId":9001,"name":"이대 정문 1층"},
       {"propertyId":9002,"name":"신촌역 앞"},
       {"propertyId":9003,"name":"연대 동문"}]'::jsonb,
     'FAILED',
     '중간에 끊긴 요약', NULL, NULL,
     'gpt-4o-mini', 'v1', false, NULL, NULL, now() + interval '1 day',
     now(), now()),

    -- B: COMPLETED + 저장함 → A 토큰으로 조회 시 404 CONSULTATION_NOT_FOUND
    (9006, 9008, 9002,
     '[{"propertyId":9004,"name":"홍대입구"}]'::jsonb,
     'COMPLETED',
     '다른 회원의 비교 컨설팅', '다른 회원 설명', '다른 회원 장단점',
     'gpt-4o-mini', 'v1', true, now() - interval '1 day', now() - interval '1 day', NULL,
     now() - interval '1 day', now());

-- ===== 목록 조회용 (A 저장본 추가: 9001 + 9007~9010 = 5개) =====

WITH runs (id, startup_condition_id, title, status, candidate_property_ids, condition_snapshot) AS (
    VALUES
        (9009, 9001, '서대문구 비교 6', 'COMPLETED', '[9001, 9002]'::jsonb, '{"title":"서대문구 카페 창업"}'::jsonb),
        (9010, 9001, '서대문구 비교 7', 'COMPLETED', '[9002, 9003]'::jsonb, '{"title":"서대문구 카페 창업"}'::jsonb),
        (9011, 9001, '서대문구 비교 8', 'COMPLETED', '[9001, 9003]'::jsonb, '{"title":"서대문구 카페 창업"}'::jsonb),
        (9012, 9001, '서대문구 비교 9', 'COMPLETED', '[9001, 9002, 9003]'::jsonb, '{"title":"서대문구 카페 창업"}'::jsonb)
)
INSERT INTO analysis_runs
(id, startup_condition_id, title, status, candidate_property_ids, property_snapshots, condition_snapshot, created_at, updated_at)
SELECT id, startup_condition_id, title, status, candidate_property_ids,
       pg_temp.fixture_property_snapshots(candidate_property_ids), condition_snapshot, now(), now()
FROM runs;

INSERT INTO comparison_ai_consultations
(id, analysis_run_id, user_id, property_snapshots, status, summary, description, pros_and_cons,
 model_name, prompt_version, is_saved, completed_at, saved_at, expires_at, created_at, updated_at)
VALUES
    (9007, 9009, 9001, '[{"propertyId":9001,"name":"이대 정문 1층"},{"propertyId":9002,"name":"신촌역 앞"}]'::jsonb,
     'COMPLETED', '목록용 요약 7', '목록용 설명 7', '목록용 장단점 7',
     'gpt-4o-mini', 'v1', true, now() - interval '4 day', now() - interval '4 day', NULL, now() - interval '4 day', now()),
    (9008, 9010, 9001, '[{"propertyId":9002,"name":"신촌역 앞"},{"propertyId":9003,"name":"연대 동문"}]'::jsonb,
     'COMPLETED', '목록용 요약 8', '목록용 설명 8', '목록용 장단점 8',
     'gpt-4o-mini', 'v1', true, now() - interval '3 day', now() - interval '3 day', NULL, now() - interval '3 day', now()),
    (9009, 9011, 9001, '[{"propertyId":9001,"name":"이대 정문 1층"},{"propertyId":9003,"name":"연대 동문"}]'::jsonb,
     'COMPLETED', '목록용 요약 9', '목록용 설명 9', '목록용 장단점 9',
     'gpt-4o-mini', 'v1', true, now() - interval '2 day', now() - interval '2 day', NULL, now() - interval '2 day', now()),
    (9010, 9012, 9001, '[{"propertyId":9001,"name":"이대 정문 1층"},{"propertyId":9002,"name":"신촌역 앞"},{"propertyId":9003,"name":"연대 동문"}]'::jsonb,
     'COMPLETED', '목록용 요약 10', '목록용 설명 10', '목록용 장단점 10',
     'gpt-4o-mini', 'v1', true, now() - interval '1 day', now() - interval '1 day', NULL, now() - interval '1 day', now());
