INSERT INTO public.regions (code, name) VALUES ('11110', '종로구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11140', '중구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11170', '용산구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11200', '성동구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11215', '광진구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11230', '동대문구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11260', '중랑구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11290', '성북구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11305', '강북구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11320', '도봉구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11350', '노원구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11380', '은평구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11410', '서대문구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11440', '마포구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11470', '양천구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11500', '강서구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11530', '구로구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11545', '금천구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11560', '영등포구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11590', '동작구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11620', '관악구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11650', '서초구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11680', '강남구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11710', '송파구') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.regions (code, name) VALUES ('11740', '강동구') ON CONFLICT (code) DO NOTHING;

INSERT INTO public.industries (id, code, category, name) VALUES (1, 'CS100001', '외식업', '한식음식점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (2, 'CS100002', '외식업', '중식음식점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (3, 'CS100003', '외식업', '일식음식점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (4, 'CS100004', '외식업', '양식음식점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (5, 'CS100005', '외식업', '제과점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (6, 'CS100006', '외식업', '패스트푸드점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (7, 'CS100007', '외식업', '치킨전문점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (8, 'CS100008', '외식업', '분식전문점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (9, 'CS100009', '외식업', '호프-간이주점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (10, 'CS100010', '외식업', '커피-음료') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (11, 'CS200001', '서비스업', '일반교습학원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (12, 'CS200002', '서비스업', '외국어학원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (13, 'CS200003', '서비스업', '예술학원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (14, 'CS200004', '서비스업', '컴퓨터학원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (15, 'CS200005', '서비스업', '스포츠 강습') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (16, 'CS200006', '서비스업', '일반의원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (17, 'CS200007', '서비스업', '치과의원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (18, 'CS200008', '서비스업', '한의원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (19, 'CS200009', '서비스업', '동물병원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (20, 'CS200010', '서비스업', '변호사사무소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (21, 'CS200011', '서비스업', '변리사사무소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (22, 'CS200012', '서비스업', '법무사사무소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (23, 'CS200013', '서비스업', '기타법무서비스') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (24, 'CS200014', '서비스업', '회계사사무소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (25, 'CS200015', '서비스업', '세무사사무소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (26, 'CS200016', '서비스업', '당구장') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (27, 'CS200017', '서비스업', '골프연습장') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (28, 'CS200018', '서비스업', '볼링장') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (29, 'CS200019', '서비스업', 'PC방') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (30, 'CS200020', '서비스업', '전자게임장') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (31, 'CS200021', '서비스업', '기타오락장') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (32, 'CS200022', '서비스업', '복권방') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (33, 'CS200023', '서비스업', '통신기기수리') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (34, 'CS200024', '서비스업', '스포츠클럽') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (35, 'CS200025', '서비스업', '자동차수리') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (36, 'CS200026', '서비스업', '자동차미용') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (37, 'CS200027', '서비스업', '모터사이클수리') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (38, 'CS200028', '서비스업', '미용실') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (39, 'CS200029', '서비스업', '네일숍') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (40, 'CS200030', '서비스업', '피부관리실') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (41, 'CS200031', '서비스업', '세탁소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (42, 'CS200032', '서비스업', '가전제품수리') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (43, 'CS200033', '서비스업', '부동산중개업') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (44, 'CS200034', '서비스업', '여관') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (45, 'CS200035', '서비스업', '게스트하우스') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (46, 'CS200036', '서비스업', '고시원') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (47, 'CS200037', '서비스업', '노래방') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (48, 'CS200038', '서비스업', '독서실') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (49, 'CS200039', '서비스업', 'DVD방') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (50, 'CS200040', '서비스업', '녹음실') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (51, 'CS200041', '서비스업', '사진관') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (52, 'CS200042', '서비스업', '통번역서비스') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (53, 'CS200043', '서비스업', '건축물청소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (54, 'CS200044', '서비스업', '여행사') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (55, 'CS200045', '서비스업', '비디오/서적임대') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (56, 'CS200046', '서비스업', '의류임대') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (57, 'CS200047', '서비스업', '가정용품임대') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (58, 'CS300001', '소매업', '슈퍼마켓') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (59, 'CS300002', '소매업', '편의점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (60, 'CS300003', '소매업', '컴퓨터및주변장치판매') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (61, 'CS300004', '소매업', '핸드폰') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (62, 'CS300005', '소매업', '주류도매') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (63, 'CS300006', '소매업', '미곡판매') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (64, 'CS300007', '소매업', '육류판매') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (65, 'CS300008', '소매업', '수산물판매') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (66, 'CS300009', '소매업', '청과상') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (67, 'CS300010', '소매업', '반찬가게') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (68, 'CS300011', '소매업', '일반의류') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (69, 'CS300012', '소매업', '한복점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (70, 'CS300013', '소매업', '유아의류') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (71, 'CS300014', '소매업', '신발') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (72, 'CS300015', '소매업', '가방') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (73, 'CS300016', '소매업', '안경') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (74, 'CS300017', '소매업', '시계및귀금속') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (75, 'CS300018', '소매업', '의약품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (76, 'CS300019', '소매업', '의료기기') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (77, 'CS300020', '소매업', '서적') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (78, 'CS300021', '소매업', '문구') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (79, 'CS300022', '소매업', '화장품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (80, 'CS300023', '소매업', '미용재료') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (81, 'CS300024', '소매업', '운동/경기용품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (82, 'CS300025', '소매업', '자전거 및 기타운송장비') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (83, 'CS300026', '소매업', '완구') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (84, 'CS300027', '소매업', '섬유제품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (85, 'CS300028', '소매업', '화초') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (86, 'CS300029', '소매업', '애완동물') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (87, 'CS300030', '소매업', '중고가구') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (88, 'CS300031', '소매업', '가구') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (89, 'CS300032', '소매업', '가전제품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (90, 'CS300033', '소매업', '철물점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (91, 'CS300034', '소매업', '악기') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (92, 'CS300035', '소매업', '인테리어') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (93, 'CS300036', '소매업', '조명용품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (94, 'CS300037', '소매업', '중고차판매') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (95, 'CS300038', '소매업', '자동차부품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (96, 'CS300039', '소매업', '모터사이클및부품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (97, 'CS300040', '소매업', '재생용품 판매점') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (98, 'CS300041', '소매업', '예술품') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (99, 'CS300042', '소매업', '주유소') ON CONFLICT DO NOTHING;
INSERT INTO public.industries (id, code, category, name) VALUES (100, 'CS300043', '소매업', '전자상거래업') ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('public.industries', 'id'), (SELECT MAX(id) FROM public.industries));


-- 회원
INSERT INTO users (id, kakao_id, nickname, login_email, created_at, updated_at
) VALUES
      (9001, 9900000001, 'owner', 'test@gmail.com', now(), now()),
      (9002, 9900000002, 'other', 'test2@gmail.com', now(), now());

-- 창업 조건
INSERT INTO startup_conditions
(id, user_id, industry_id, title, preferred_region_code,
 total_capital, available_capital, loan_amount,
 monthly_labor_cost, cost_of_sales_rate, monthly_other_operating_cost,
 created_at, updated_at)
VALUES
    (9001, 9001, 1, '서대문구 카페 창업', '11410',
     150000000, 100000000, 50000000,
     4000000, 35.00, 1000000,
     now(), now()),
    (9002, 9002, 1, '마포구 카페 창업', '11440',
     200000000, 120000000, 80000000,
     5000000, 33.50, 1200000,
     now(), now());

-- 후보 매물 (A: 3개, B: 1개)
INSERT INTO candidate_properties
(id, startup_condition_id, name, address, address_detail, latitude, longitude,
 area_m2, deposit, monthly_rent, key_money, monthly_management_fee,
 mapping_status, created_at, updated_at)
VALUES
    (9001, 9001, '이대 정문 1층', '서울특별시 서대문구 이화여대길 52', '101호', 37.5601, 126.9458,
     66.12, 30000000, 2500000, 40000000, 200000, 'MAPPED', now(), now()),
    (9002, 9001, '신촌역 앞',     '서울특별시 서대문구 신촌역로 30',   NULL,   37.5598, 126.9425,
     49.50, 20000000, 1800000, 25000000, 150000, 'MAPPED', now(), now()),
    (9003, 9001, '연대 동문',     '서울특별시 서대문구 연세로 50',     NULL,   37.5640, 126.9380,
     33.00, 10000000, 1200000, 0,        100000, 'MAPPED', now(), now()),
    (9004, 9002, '홍대입구',      '서울특별시 마포구 양화로 160',      NULL,   37.5572, 126.9245,
     59.40, 50000000, 3500000, 60000000, 250000, 'MAPPED', now(), now());

-- 분석 실행 (회원별 1회)

INSERT INTO analysis_runs
(id, startup_condition_id, status, candidate_property_ids, condition_snapshot,
 created_at, updated_at)
VALUES
    (9001, 9001, 'COMPLETED', '[9001, 9002, 9003]'::jsonb,
     '{"title":"서대문구 카페 창업","totalCapital":150000000,"availableCapital":100000000}'::jsonb,
     now(), now()),
    (9002, 9002, 'COMPLETED', '[9004]'::jsonb,
     '{"title":"마포구 카페 창업","totalCapital":200000000,"availableCapital":120000000}'::jsonb,
     now(), now());

-- 매물 분석 (매물당 1개)
INSERT INTO property_analyses
(id, analysis_run_id, candidate_property_id, property_snapshot, created_at, updated_at)
VALUES
    (9001, 9001, 9001, '{"name":"이대 정문 1층","deposit":30000000,"monthlyRent":2500000}'::jsonb, now(), now()),
    (9002, 9001, 9002, '{"name":"신촌역 앞","deposit":20000000,"monthlyRent":1800000}'::jsonb,     now(), now()),
    (9003, 9001, 9003, '{"name":"연대 동문","deposit":10000000,"monthlyRent":1200000}'::jsonb,     now(), now()),
    (9004, 9002, 9004, '{"name":"홍대입구","deposit":50000000,"monthlyRent":3500000}'::jsonb,      now(), now());

-- AI 컨설팅
INSERT INTO property_ai_consultations
(id, property_analysis_id, status, summary, risk_factors, improvement_suggestions,
 contract_considerations, model_name, prompt_version, completed_at, created_at, updated_at)
VALUES
    -- A: COMPLETED (개선 방안 순서를 일부러 섞고, priority null 하나 포함)
    (9001, 9001, 'COMPLETED',
     '대학가 유동인구가 많아 매출 잠재력은 높지만 월세 부담이 큰 편입니다.',
     '[{"level":"HIGH","content":"월세가 예상 매출의 15%를 넘습니다."},
       {"level":"MEDIUM","content":"방학 기간 매출 감소 폭이 큽니다."}]'::jsonb,
     '[{"priority":3,"content":"방학 기간 운영 시간을 조정하세요."},
       {"priority":null,"content":"배달 채널을 병행해보세요."},
       {"priority":1,"content":"월세 인하를 협상하세요."},
       {"priority":2,"content":"권리금 근거 자료를 요청하세요."}]'::jsonb,
     '계약 기간 2년 이상, 권리금 보호 조항을 확인하세요.',
     'gpt-4o-mini', 'v1', now(), now(), now()),
    -- A: PENDING
    (9002, 9002, 'PENDING', NULL, NULL, NULL, NULL, 'gpt-4o-mini', 'v1', NULL, now(), now()),
    -- A: FAILED (내용이 일부 남아 있어도 응답에서는 null이어야 함)
    (9003, 9003, 'FAILED', '중간에 끊긴 요약', NULL, NULL, NULL, 'gpt-4o-mini', 'v1', NULL, now(), now()),
    -- B: COMPLETED
    (9004, 9004, 'COMPLETED', '다른 회원의 컨설팅',
     '[]'::jsonb, '[]'::jsonb, NULL, 'gpt-4o-mini', 'v1', now(), now(), now());

