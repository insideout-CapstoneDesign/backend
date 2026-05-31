# Place Search Elasticsearch 초기 적재 가이드

## 목적
- 등록된 `building` 데이터를 기반으로 `places_v1` 인덱스를 초기 구성합니다.
- 최초 배포 또는 재색인 시, 정상 트래픽 전에 1회 실행하는 용도입니다.

## 동작 방식
- 애플리케이션 시작 시 `PlaceSearchBootstrapRunner`가 활성화된 경우에만 실행됩니다.
- `building` 데이터를 페이지 단위로 읽어 Elasticsearch에 bulk upsert 합니다.
- upsert 방식이라 동일 작업을 다시 실행해도 안전합니다(멱등).

## 설정 값
- `PLACE_SEARCH_BOOTSTRAP_ENABLED` (기본값: `false`)
- `PLACE_SEARCH_BOOTSTRAP_BATCH_SIZE` (기본값: `500`, 최대: `2000`)

`application.yml` 매핑:
- `place.search.bootstrap.enabled`
- `place.search.bootstrap.batch-size`

## 1회 초기 적재 실행(권장)
1. Elasticsearch가 실행 중이고 연결 가능한 상태인지 확인합니다.
2. 아래 명령으로 bootstrap을 켜고 애플리케이션을 1회 실행합니다.

```bash
PLACE_SEARCH_BOOTSTRAP_ENABLED=true PLACE_SEARCH_BOOTSTRAP_BATCH_SIZE=500 ./gradlew bootRun
```

3. 시작 로그에서 아래 메시지를 확인합니다.
- `[PlaceSearchBootstrap] ES initial backfill started...`
- `[PlaceSearchBootstrap] ES initial backfill completed...`

4. 초기 적재가 끝나면 일반 실행에서는 bootstrap을 비활성화합니다.
- 운영 기본값은 `PLACE_SEARCH_BOOTSTRAP_ENABLED=false` 유지

## 검증 방법
- `GET /places_v1/_count`
- 검색 API 스모크 테스트:
  - `GET /api/v1/places/search?q=신세계&size=10`
