# 로컬 개발 DB 스키마 적용

PostgreSQL 17의 로컬 `irion_dev.public` 전용 수동 SQL이다. 저장소의 `src/main/resources/data/init-data.sql`은 지역·업종 초기 데이터용이고, 기본 JPA 설정은 `validate`다.

| DB 상태 | 실행할 파일 |
|---|---|
| 구버전 창업 조건·매물 테이블이 비어 있음 | 01 실행. 분석 테이블이 둘 다 없으면 이어서 02 실행 |
| 최신 창업 조건·매물 테이블이 있고 분석 테이블만 둘 다 없음 | 02만 실행 |
| 두 분석 테이블이 있고 기존 02 구조이며 분석 데이터와 `property_snapshots` 컬럼이 없음 | 03만 실행 |
| 이미 `property_snapshots` 컬럼이 있음 | 재적용 없이 아래 검증만 수행 |
| 신규 컬럼이 없고 기존 분석 데이터가 있음 | 중단하고 별도 이관 정책 검토. 과거 입력을 기본값으로 채우지 않음 |
| 부모 테이블 미구축, 부분 적용, 01 대상에 기존 행이 있거나 제약이 다름 | 중단하고 스키마·데이터 이관 방법을 별도 검토 |

프로젝트 루트의 PowerShell에서 로컬 PostgreSQL 클라이언트를 사용한다. 로그인 역할은 본인의 로컬 설정을 입력한다. 앱을 종료한 뒤 백업하고, 접속 대상과 구조를 먼저 확인한다.

```powershell
$dbRole = Read-Host '로컬 PostgreSQL 사용자'
$conn = @('-h','127.0.0.1','-p','5433','-U',$dbRole,'-d','irion_dev')
$backup = Join-Path $env:TEMP ("irion_dev-{0}.dump" -f (Get-Date -Format yyyyMMdd-HHmmss))
pg_dump @conn -Fc -f $backup
if ($LASTEXITCODE -ne 0) { throw '백업 실패' }
psql @conn -X -c 'SELECT current_database(), current_schema(), version()'
psql @conn -X -c '\dt public.*'
psql @conn -X -c '\d+ public.startup_conditions'
psql @conn -X -c '\d+ public.candidate_properties'
psql @conn -X -c 'SELECT (SELECT count(*) FROM public.startup_conditions), (SELECT count(*) FROM public.candidate_properties)'
```

01의 구버전 기준은 `planned_opening_month` 및 `commercial_area_id`가 있고, 상권 FK `fk8onr25j9d3lrd72bant6c8kyr`가 `commercial_areas(id)`를 참조하며, `candidate_properties_mapping_status_check`가 PENDING/MAPPED/FAILED를 허용하는 상태다. 새 컬럼·사용자별 UNIQUE가 이미 있으면 01을 건너뛰고 전체 구조를 비교한다. 분석 테이블이 이미 있다면 두 테이블의 행 수도 확인한다. 02는 부모 두 테이블에 bigint PK `id`가 있어야 한다.

아래에서 표에 해당하는 명령만 선택한다. 각 파일은 단일 트랜잭션이다. 01은 잠금 후 대상 행 수를 재검사한다. 오류가 나면 psql이 종료되고 미커밋 변경이 롤백된다. 원인을 확인한 후 다음 파일 진행 여부를 결정한다.

```powershell
psql @conn -X -v ON_ERROR_STOP=1 -f db/manual/01-update-empty-startup-property.sql
if ($LASTEXITCODE -ne 0) { throw '01 적용 실패' }
```

```powershell
psql @conn -X -v ON_ERROR_STOP=1 -f db/manual/02-create-analysis-tables.sql
if ($LASTEXITCODE -ne 0) { throw '02 적용 실패' }
```

03 대상이면 새 컬럼의 부재와 기존 02 구조를 먼저 확인한다. 03은 잠금 후 행과 대상 컬럼 구성을 재검사한다.

```powershell
psql @conn -X -v ON_ERROR_STOP=1 -f db/manual/03-add-analysis-property-snapshots.sql
if ($LASTEXITCODE -ne 0) { throw '03 적용 실패' }
```

02 또는 03 적용 후 `property_snapshots`가 기본값 없는 jsonb NOT NULL이고, CHECK가 비어 있지 않은 JSON 배열을 요구하는지 확인한다. 매물 ID 배열과의 위치별 일치는 Java 팩터리에서 검사한다.

적용 후 앞의 두 `\d+` 명령을 반복하고 분석 테이블도 확인한다. 01 적용 시 금액 3개는 bigint NOT NULL, 원가율은 numeric(5,2) NOT NULL, user_id는 UNIQUE, 새 상권 코드는 nullable varchar(20), 상태 CHECK는 MAPPED/FAILED여야 한다. 사용자·업종·창업 조건 FK가 유지됐는지 확인한다. 02의 JSONB·NULL·상태 CHECK, FK와 조회 인덱스는 파일 정의와 대조한다.

```powershell
psql @conn -X -c '\d+ public.analysis_runs'
psql @conn -X -c '\d+ public.property_analyses'
.\gradlew.bat bootRun --args=--spring.jpa.hibernate.ddl-auto=validate
```

기동 로그에서 접속 대상, EntityManagerFactory 초기화 및 애플리케이션 시작을 확인한 뒤 Ctrl+C로 종료한다.

## 비교 컨설팅 재시도 (06)

05 적용 DB에서는 기존 앱의 AI 생성 작업과 쓰기를 종료한 뒤 `06-comparison-generation-attempts.sql`을 한 번 적용하고 신버전을 기동한다. 기존 행은 첫 번째 시도로 간주해 시작 시각을 `created_at`으로 채운다. 새 스키마를 Hibernate로 생성하는 환경에는 엔티티 매핑이 두 컬럼을 생성한다.
