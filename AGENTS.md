# AGENTS.md

## 0. 목적
이 문서는 **실내외 통합 내비게이션 'INSIDE OUT'** 백엔드 개발 시, 코드 에이전트(Codex 등)가 반드시 지켜야 하는 실행 규칙 및 검증 절차를 정의합니다. 에이전트는 이 규칙을 준수하여 승은(Member 4)의 개발을 보조합니다.

---

## 1. 필수 도구/명령 (대체 금지)
### 1.1 빌드 및 실행
- 빌드 도구: Gradle
- 실행 명령: `./gradlew bootRun`
- 테스트 실행: `./gradlew test` (작업 후 반드시 실행하여 전체 정합성 확인)

### 1.2 DB 마이그레이션 (Flyway)
- 위치: `src/main/resources/db/migration`
- 규칙: 기존 `V1`~`V6` 파일 수정 절대 금지. 변경 사항은 반드시 `V7__...`, `V8__...` 식으로 신규 파일을 추가함.

---

## 2. 프로젝트 아키텍처 규칙
### 2.1 DDD(Domain-Driven Design) 준수
- 작업 주 무대: `com.insideout.backend.domain.navigation`
- 계층 간 규칙:
    - **Controller**: 외부 요청(민지/프론트) 수신 및 DTO 반환.
    - **Service**: 비즈니스 로직(경로 계산, API 통합) 처리.
    - **Facade**: 타 도메인(Map, Building) 데이터 조회 시 `MapQueryFacade` 등을 통해 접근.
    - **Infrastructure**: 외부 API(카카오, 티맵) 호출 구현체 위치.

### 2.2 금지 사항
- 도메인 서비스에서 직접 Repository를 참조하여 타 도메인 데이터를 수정하는 행위 금지.
- 외부 API 키(Kakao, Tmap)를 코드에 하드코딩 금지 (`application.yml` 활용).

---

## 3. 내비게이션 시스템 특화 규칙
### 3.1 실외 경로 검색 (현재 최우선 과제)
- **우선순위**: 실내 알고리즘 전, 실외(대중교통/자동차/도보) API 연동을 먼저 완성한다.
- **도착지 보정 로직**: 사용자가 검색한 목적지가 우리 DB에 등록된 건물일 경우, 해당 건물의 **가장 가까운 입구 앵커(Anchor) 좌표**를 찾아 이를 목적지로 하여 외부 API를 호출한다.

### 3.2 데이터 포맷 (프론트엔드 협업)
- 모든 응답은 민지(Member 3)가 처리하기 쉬운 JSON 구조(DTO)로 설계한다.
- 소요 시간(`duration`) 정보는 외부 API 결과를 그대로 따르되, 실내 이동이 포함될 경우 "X분 + 실내 이동" 형태의 문자열 조합을 준비한다.

---

## 4. 코딩 및 커밋 컨벤션
- **언어**: Java 17 / Spring Boot
- **네이밍**: 클래스는 `PascalCase`, 변수/메서드는 `camelCase`.
- **커밋 메시지**:
    - `feat`: 기능 추가
    - `fix`: 버그 수정
    - `db`: 마이그레이션 파일 추가
    - `docs`: 문서 수정

---

# 추가. 백엔드 API 응답 및 예외 처리 가이드
우리 프로젝트는 프론트엔드와의 원활한 통신과 디버깅을 위해 모든 API 응답을 ApiResponse라는 단일 규격으로 통일합니다.

## 1. 응답 구조 (ApiResponse)
모든 응답은 JSON 바디에 아래 5가지 필드를 포함합니다.
```
isSuccess: 성공 여부 (true/false)

code: 비즈니스 상태 코드 (예: COMMON200_1, MEMBER404_1)

message: 응답 메세지

timestamp: 응답 생성 시각 (자동 생성)

result: 실제 반환 데이터 (없을 경우 null)
```
## 2. 성공 응답 사용법 (ApiResponse.success)
성공 시에는 컨트롤러에서 ApiResponse의 정적 메서드를 호출하여 반환합니다.

```java
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(GeneralSuccessCode.CREATED, userService.signup(request));
    }
```

GenralSuccessCode에 정의되어있는 OK,Created, NoContent 등의 코드들을 확인해주세요. 자신이 구현한 메서드의 특성에 맞게 사용하시면 됩니다.

만약 자신의 도메인에서 특정 성공 응답 반환이 필요할 경우 [도메인]SuccessCode 의 Enum 클래스를 생성하여 커스텀해주세요!
```java
@Getter
@RequiredArgsConstructor
public enum ExampleSuccessCode implements BaseSuccessCode{

    SIGNUP_COUPON(HttpStatus.OK,
            "EX200_1",
            "회원가입 후 쿠폰이 발급 되었습니다.."),
    SIGNUP_NORMAL(HttpStatus.OK,
            "EX200_2",
            "회원가입이 완료되었습니다.")
    ;
    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

사용 방식은 GeneralSuccessCode와 동일합니다.
```java
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(ExampleSuccessCode.SIGNUP_COUPON, userService.signup(request));
    }
```
현재 PR의 설계처럼 "항상 HTTP 200, body의 code로 구분" 하는 방식은 내부 팀 API에선 충분히 쓰이는 패턴입니다. 다만 GeneralSuccessCode.CREATED처럼 의미 있는 성공 코드를 정의해두고도 HTTP 상태는 200으로만 내려간다면, 해당 enum 값이 body의 코드 문자열 역할만 하고 getStatus()는 사용되지 않는다는 점을 팀 내에서 명확히 인지하고 계시면 됩니다.

## 5. 작업 시작 전 체크리스트
- [ ] 현재 수정하려는 파일이 `domain/navigation` 폴더 내에 있는가?
- [ ] 외부 API 호출 시 예외 처리가 되어 있는가?
- [ ] 신규 API 추가 시 `NavigationController`에 엔드포인트를 정의했는가?
- [ ] 민지(프론트)에게 전달할 DTO 구조가 명확한가?