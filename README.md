# 📍 Insideout - AI 기반 실내외 통합 내비게이션 (Backend API Server)

> **"외부 지도와 실내 도면을 하나의 길찾기 경험으로 연결하는 백엔드."**
> Insideout Backend는 사용자 웹앱과 관리자 웹앱이 공통으로 사용하는 API 서버입니다. 장소 검색, 실내외 통합 경로 탐색, AI 도면 분석 결과 관리, 지도 편집 Draft/Publish 파이프라인, 건물·층·출입구·POI 데이터 관리를 담당하며 상용 지도 API와 자체 실내 그래프 데이터를 연결해 최종 목적지까지 이어지는 하이브리드 내비게이션 서비스를 제공합니다.

---
## 👥 팀원 소개 (Contributors)

> **Insideout 프로젝트를 이끈 양양양말을 소개합니다.**


| **차승은** | **이민지** | **김민준** | **김세현** |
| :---: | :---: | :---: | :---: |
| [<img src="https://github.com/user-attachments/assets/35081664-ee95-49bf-9bbf-0340df69f54b" height="180" width="130" style="border-radius: 8px;"><br/>](https://github.com/cktmddms) | [<img src="https://github.com/user-attachments/assets/8d75a543-b6ef-4a57-86c2-e06d93e9376d" height="180" width="130" style="border-radius: 8px;"><br/>](https://github.com/thisminji) | [<img src="https://github.com/user-attachments/assets/d6335e5f-31a8-4ab6-9432-1269227ae012" height="180" width="130" style="border-radius: 8px;"><br/>](https://github.com/minjune0) | [<img src="https://github.com/user-attachments/assets/40120ba5-e3c7-4048-9d54-cdfa837f7a6d" height="180" width="130" style="border-radius: 8px;"><br/>](https://github.com/sekong11) |
| 🔹 **Hybrid Navigation** <br> <sub>사용자 웹 - BE, FE</sub> | 🔹 **Auth, Search, Infra** <br> <sub>사용자 웹 - BE, FE</sub> | 🔹 **AI Map Builder** <br> <sub>관리자 웹 - AI, FE</sub> | 🔹 **Map Editor** <br> <sub>관리자 웹 - BE, FE</sub> |

---

## 🚀 백엔드 핵심 역할 (Core Responsibilities)

### 🔍 1. 등록 장소와 외부 장소를 통합하는 검색 API

사용자가 입력한 키워드를 기준으로 Insideout에 실내 지도가 구축된 등록 건물/POI와 Kakao Local 기반 외부 장소를 함께 조회합니다. 등록된 장소는 `placeId`, `buildingId`, `poiId`, `isRegistered` 메타데이터를 포함해 프론트엔드가 곧바로 실내 목적지 길찾기로 이어갈 수 있도록 설계했습니다.

* 키워드 검색: 등록 장소 우선 조회 후 외부 장소 병합
* 자동완성: Elasticsearch 결과를 우선 사용하고 부족한 경우 Kakao Local fallback
* 지도 클릭 조회: 좌표 기반 가장 가까운 등록 장소 또는 외부 장소 탐색
* 상세 조회: `placeId` 또는 `externalApiId` 기반 건물/실내 POI 정보 제공

---

### 🧭 2. 실외-실내 하이브리드 경로 탐색 엔진

Tmap의 도보·자동차·대중교통 경로와 자체 실내 그래프 라우팅을 결합해 하나의 응답으로 제공합니다. 목적지가 실내 POI인 경우 건물 출입구 매핑 정보를 기준으로 외부 경로의 도착점을 보정하고, 이후 실내 노드/엣지 그래프를 따라 최종 목적지까지의 경로를 이어 붙입니다.

* `WGS84` 좌표 기반 외부 경로와 픽셀 좌표 기반 실내 경로를 하나의 `Leg` 목록으로 반환
* 도보, 자동차, 대중교통 옵션을 동일한 응답 구조로 제공
* 실내-only, 실외-to-실내, 실내-to-실외, 실내-to-실내 시나리오 지원
* 계단/장애물 가중치 조정을 통한 `최단 거리` 및 `편안한 길` 옵션 제공
* 층 이동 시 엘리베이터/계단 등 수직 이동수단을 별도 단계로 표현

---

### 🗺️ 3. 관리자 지도 편집 Draft/Publish 파이프라인

관리자 웹앱에서 AI가 추출한 도면 객체를 바탕으로 실내 지도 데이터를 편집하고, 검수 완료 후 사용자 서비스에 노출 가능한 Published Map으로 전환합니다.

* 건물 단위 Draft 초기화 및 층별 편집 데이터 조회
* Node, Edge, Zone, POI, Floorplan Object 저장
* 엘리베이터/계단 등 수직 이동수단 생성·수정·삭제·층별 노드 매핑
* POI와 Kakao 장소 ID 및 위경도 좌표 매핑
* Draft 검증 후 Published 버전으로 배포

---

### 🤖 4. AI 도면 분석 서비스 연동

별도 AI 서버와 통신해 실내 도면 및 캠퍼스 지도 이미지 분석을 요청하고, 감지된 객체를 백엔드 데이터 모델에 저장합니다. 이후 지도 편집기는 저장된 detection 결과를 기반으로 Draft 맵을 초기화합니다.

* 층별 실내 도면 분석 요청 및 detection 결과 조회
* 캠퍼스 지도 분석 요청 및 detection 결과 조회
* AI 서버 API Key 헤더 기반 내부 서비스 인증
* AI 분석 결과를 관리자 편집 플로우의 초기 데이터로 활용

---

### 🏢 5. 멀티 테넌트 기반 건물·캠퍼스 운영 관리

관리자는 본인이 소속된 Tenant 범위 안에서 캠퍼스, 건물, 층, 도면, 출입구, 출입구-노드 매핑을 관리합니다. 주요 관리자 API는 JWT 인증 사용자와 `tenantId` 소속 관계를 검증한 뒤 실행됩니다.

* Tenant 생성 및 활성/비활성 상태 관리
* 건물, 층, 도면, 출입구 생성 및 조회
* 캠퍼스 지도 업로드 및 관리
* S3 호환 스토리지에 도면/지도 이미지 저장
* JWT 기반 사용자/관리자 인증

---

## 📌 주요 API 명세 요약

### Place API

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/places/search` | 키워드 기반 장소 검색 |
| `GET` | `/api/v1/places/suggest` | 검색어 자동완성 |
| `GET` | `/api/v1/places/nearest` | 좌표 기준 가장 가까운 장소 조회 |
| `GET` | `/api/v1/places/detail` | `placeId` 또는 `externalApiId` 기반 상세 조회 |

### Navigation API

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/navigation/routes` | 도보/자동차/대중교통 및 실내 경로 통합 조회 |
| `POST` | `/api/v1/navigation/transit/routes` | 대중교통 중심 경로 조회 |

### Map Editor API

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/map-editor/buildings/{buildingId}/floors/{floorId}` | 층별 에디터 초기 데이터 조회 |
| `POST` | `/api/v1/map-editor/buildings/{buildingId}/initialize-draft` | 건물 Draft 일괄 초기화 |
| `PUT` | `/api/v1/map-editor/buildings/{buildingId}/floors/{floorId}/draft` | 층별 Draft 저장 |
| `POST` | `/api/v1/map-editor/buildings/{buildingId}/publish` | Draft 최종 배포 |
| `GET` | `/api/v1/map-editor/buildings/{buildingId}/vertical-connectors` | 수직 이동수단 목록 조회 |
| `POST` | `/api/v1/map-editor/buildings/{buildingId}/vertical-connectors` | 수직 이동수단 생성 |
| `PATCH` | `/api/v1/map-editor/buildings/{buildingId}/vertical-connectors/{connectorId}` | 수직 이동수단 수정 |
| `DELETE` | `/api/v1/map-editor/buildings/{buildingId}/vertical-connectors/{connectorId}` | 수직 이동수단 삭제 |
| `PUT` | `/api/v1/map-editor/buildings/{buildingId}/poi-mappings` | POI 외부 장소 매핑 저장 |

### AI API

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/ai/floorplans/{floorplanId}/analyze` | 실내 도면 AI 분석 요청 |
| `GET` | `/api/v1/ai/floorplans/{floorplanId}/detections` | 실내 도면 분석 결과 조회 |
| `POST` | `/api/v1/ai/campuses/{campusMapId}/analyze` | 캠퍼스 지도 AI 분석 요청 |
| `GET` | `/api/v1/ai/campuses/{campusMapId}/detections` | 캠퍼스 지도 분석 결과 조회 |

### Building / Campus / Tenant API

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/buildings` | 건물 목록 조회 |
| `POST` | `/api/v1/buildings` | 건물 생성 |
| `POST` | `/api/v1/buildings/{buildingId}/floors` | 층 생성 |
| `POST` | `/api/v1/buildings/{buildingId}/floors/{floorId}/floorplans` | 층 도면 업로드 |
| `GET` | `/api/v1/buildings/{buildingId}/entrances` | 건물 출입구 조회 |
| `POST` | `/api/v1/buildings/{buildingId}/entrances` | 건물 출입구 생성 |
| `POST` | `/api/v1/buildings/{buildingId}/entrance-mappings` | 출입구와 실내 노드 매핑 |
| `GET` | `/api/v1/campuses` | 캠퍼스 목록 조회 |
| `POST` | `/api/v1/campuses` | 캠퍼스 생성 |
| `POST` | `/api/v1/campuses/{campusId}/maps` | 캠퍼스 지도 업로드 |
| `GET` | `/api/v1/tenants/me` | 내 Tenant 목록 조회 |
| `POST` | `/api/v1/tenants/create` | Tenant 생성 |

---

## 📂 프로젝트 구조

```text
src/main/java/com/insideout/backend
├── domain
│   ├── ai            # AI 도면/캠퍼스 분석 연동
│   ├── building      # 캠퍼스, 건물, 층, 도면, 출입구 관리
│   ├── map           # 실내 지도 편집, Draft/Publish, Published Map 조회
│   ├── navigation    # 실내외 통합 경로 탐색
│   ├── place         # 장소 검색, 자동완성, 상세/근접 조회
│   ├── tenant        # 멀티 테넌트 관리
│   └── user          # 인증 및 사용자 조회
├── global
│   ├── apiPayload    # 공통 응답/예외 포맷
│   ├── config        # OpenAPI, RestTemplate 등 공통 설정
│   ├── infra         # Kakao, AI, S3 외부 인프라 설정
│   └── security      # Spring Security + JWT
└── resources
    ├── db/migration  # Flyway migration
    └── application*.yml
```

---

## 🔥 기술적 도전 및 해결 과제 (Technical Challenges)

### 1. 외부 장소와 등록 실내 POI의 단일 검색 응답 모델 구성

* **문제:** Kakao Local 검색 결과와 Insideout 등록 건물/POI는 식별자, 좌표, 실내 지도 보유 여부가 서로 다릅니다.
* **해결:** 검색 응답에 `externalApiId`, `buildingId`, `poiId`, `isRegistered`를 함께 내려주는 단일 DTO를 구성했습니다. 프론트엔드는 동일한 검색 목록에서 일반 장소와 실내 경로 탐색 가능한 장소를 구분하고, 상세 조회 및 길찾기 요청까지 같은 파이프라인으로 이어갈 수 있습니다.

---

### 2. 외부 경로 API 실패에도 가능한 경로를 반환하는 부분 성공 전략

* **문제:** 도보, 자동차, 대중교통, 실내 라우팅은 각각 다른 외부/내부 데이터에 의존하므로 일부 수단만 실패할 수 있습니다.
* **해결:** `routes`, `notFoundRouteTypes`, `failures`, `routeMessage`를 분리해 응답합니다. 하나의 이동 수단이 실패해도 전체 요청을 실패시키지 않고, 가능한 경로와 실패 사유를 함께 제공해 사용자 화면에서 자연스럽게 fallback UI를 구성할 수 있게 했습니다.

---

### 3. Draft 지도와 Published 지도의 분리

* **문제:** 관리자가 편집 중인 지도 데이터가 즉시 사용자 서비스에 노출되면 검수 전 경로 오류가 발생할 수 있습니다.
* **해결:** 편집 중인 데이터는 Draft MapVersion에 저장하고, 검수 완료 시 Published MapVersion으로 전환합니다. 사용자 앱은 Published 데이터만 조회하고, 관리자 앱은 Draft를 초기화·수정·배포하는 별도 플로우를 사용합니다.

---

### 4. 층간 이동을 포함한 그래프 라우팅 가중치 설계

* **문제:** 실내 경로는 단순 2D 최단 거리뿐 아니라 엘리베이터, 계단, 장애물, 층 이동 비용을 함께 고려해야 합니다.
* **해결:** 노드/엣지 그래프에 수직 이동수단과 장애물 가중치를 반영하고, 경로 옵션에 따라 계단/장애물 패널티를 다르게 적용했습니다. 이를 통해 빠른 길과 편안한 길을 같은 그래프 구조 위에서 계산할 수 있습니다.

---

### 5. AI 분석 결과를 운영 가능한 지도 데이터로 전환

* **문제:** AI detection 결과는 그대로 사용자 길찾기에 사용할 수 있는 정제된 지도 데이터가 아닙니다.
* **해결:** AI 결과를 Draft 초기 데이터로 변환하고, 관리자가 Node/Edge/POI/Zone을 직접 검수·보정한 뒤 Published Map으로 배포하는 구조를 채택했습니다. 자동화의 속도와 운영 데이터의 신뢰성을 동시에 확보했습니다.

---

## 🧾 공통 응답 포맷

모든 API는 `ApiResponse` 래퍼를 통해 성공/실패 응답 구조를 통일합니다.

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "성공입니다.",
  "result": {}
}
```

예외는 도메인별 ErrorCode와 `GlobalExceptionHandler`를 통해 일관된 HTTP 응답으로 변환됩니다.


---
## 🛠️ 기술 스택 (Tech Stack)

### Core & Framework
<div>
  <img src="https://img.shields.io/badge/Java%2017-007396?style=for-the-badge&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
</div>

### Security
<div>
  <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white">
  <img src="https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white">
</div>

### ORM / Data Access
<div>
  <img src="https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=for-the-badge">
  <img src="https://img.shields.io/badge/Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white">
</div>

### Database / Search
<div>
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/PostGIS-2596BE?style=for-the-badge">
  <img src="https://img.shields.io/badge/Elasticsearch-005571?style=for-the-badge&logo=elasticsearch&logoColor=white">
</div>

### Storage
<div>
  <img src="https://img.shields.io/badge/MinIO-C72E49?style=for-the-badge">
  <img src="https://img.shields.io/badge/S3%20Compatible%20API-569A31?style=for-the-badge&logo=amazons3&logoColor=white">
</div>

### API / External API
<div>
  <img src="https://img.shields.io/badge/REST%20API-FF6B6B?style=for-the-badge">
  <img src="https://img.shields.io/badge/Kakao%20Map%20API-FFCD00?style=for-the-badge&logo=kakao&logoColor=000000">
  <img src="https://img.shields.io/badge/Tmap%20API-005BAC?style=for-the-badge">
</div>

### Deployment / CI-CD
<div>
  <img src="https://img.shields.io/badge/Microsoft%20Azure-0078D4?style=for-the-badge&logo=microsoftazure&logoColor=white">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white">
</div>



## 🏗️ 시스템 아키텍처 (System Architecture)
<img width="1284" height="1346" alt="image" src="https://github.com/user-attachments/assets/f370cdcf-9326-4523-aab4-935b1c500cef" />



## 🔄 데이터 흐름도 (Data Flow)


## 💾 데이터베이스 모델링 (ERD)
