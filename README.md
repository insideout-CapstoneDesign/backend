# Git Flow & Convention
간소화된 GitHub Flow를 기반으로, Issue를 먼저 생성하고 이를 해결하기 위해 Branch를 파고 PR을 보내는 순서로 개발합니다.

## ⚠️ 중요 규칙
- main: 실제 서비스가 배포되는 최종 운영 코드 (직접 Push 절대 금지 ❌)
- dev: 개발 중인 코드의 중심 (Base Branch)
- feat/...: 실제 작업 브랜치 (develop에서 분기점 생성 후 다시 dev으로 머지)

## 1️⃣ Commit Message Convention
### Prefix: <subject> (#<issue_number>)
예시)
- feat: 구글 로그인 API 기능 구현 (#11)
- fix: A* 알고리즘 무한 루프 버그 수정 (#10)
- refactor: POI 검색 쿼리 성능 개선 (#3)
- chore: lombok 의존성 추가 (#5)
- docs: API 명세서 최신화 (#4)

## 🏷 prefix 목록
- **feat** : 새로운 기능 구현 `[feat] 구글 로그인 API 기능 구현 (#11)`
- **fix** : 코드 오류 수정 `[fix] 회원가입 비즈니스 로직 오류 수정 (#10)`
- **design** : CSS, UI/UX 레이아웃 변경 `[design] 메인 화면 로그인 버튼 위치 변경 (#9)`
- **del** : 쓸모없는 코드 삭제 `[del] 불필요한 import 제거 (#12)`
- **docs** : README나 wiki 등의 문서 개정 `[docs] 리드미 수정(#14)`
- **refactor** : 내부 로직은 변경 하지 않고 기존의 코드를 개선하는 리팩터링 `[refactor] 코드 로직 개선(#15)`
- **chore** : 의존성 추가, yml 추가와 수정, 패키지 구조 변경, 파일 이동 `[chore] yml 수정(#21)`, `[chore] MAC-8 lombok 의존성 추가(#22)`
- **test**: 테스트 코드 작성, 수정 `[test] 로그인 API 테스트 코드 작성(#20)`
- **style** : 코드에 관련 없는 주석 달기, 줄바꿈

## 2️⃣ Issue Creation Rules
기능 구현 전, 반드시 이슈를 먼저 생성합니다.
1. 템플릿 선택: New Issue 버튼을 누르고 Feature 또는 Bug 템플릿을 선택합니다.
2. 제목 작성: [Prefix] 명확한 작업 내용 형태로 작성합니다.
   - 예: [feat] 엘리베이터 가중치 반영 API
3. 개발라벨(Label) 설정: Feature, Bug, Refactor 등 작업의 성격을 지정합니다.
4. Assignees 설정: 작업을 담당할 본인을 지정합니다.

## 3️⃣ Issue Hierarchy (Parent-Child Structure)
우리는 큰 단위의 작업(Epic)에 세부 구현 작업(Task)을 링크하여 진행률을 관리합니다.
1. 상위 이슈(Epic) 생성먼저 며칠에 걸쳐 진행할 큰 단위의 기능(Epic)을 생성합니다. (예: [Epic] 하이브리드 길 찾기 엔진 구축)
2. 하위 이슈(Task) 생성실제 하루 단위로 끝낼 수 있는 세부 구현 작업들을 생성합니다. (예: [feat] 노드 조회 레포지토리 로직 작성 (#12))
3. 링크 연결 (Tracking)상위 이슈(Epic)의 본문에 하위 이슈 번호를 체크리스트 문법으로 작성하여 진행도를 한눈에 파악합니다.

## 🎯 하위 작업 목록 (Tasks)
- [x] #12  <-- 이슈 번호만 적으면 GitHub가 제목을 자동 완성해 줍니다.
- [ ] #13

## 4️⃣ Branch & PR Rules
이슈가 생성되면 해당 이슈 번호를 기반으로 브랜치를 생성합니다.
### 🌱 Branch Naming
타입/#이슈번호-영문설명
- feat/#12-jwt-login
- fix/#15-astar-timeout
### 🚀 Pull Request (PR) 
1. Process작업 전 최신화: 작업 시작 전 항상 dev 브랜치를 pull 받아서 최신 상태를 유지합니다.
2. PR 제목: 이슈 제목과 동일하게 맞추거나 작업 성격이 한눈에 보이게 작성합니다.예: feat: 건물 관리자 JWT 로그인 구현 (#12)이슈 연결:
3. PR 내용(Description) 상단에 Closes #이슈번호를 반드시 적어 머지 시 이슈가 자동 종료되도록 합니다.
4. 코드 리뷰: 최소 **1명 이상의 팀원에게 Approve(승인)**를 받아야하며 리뷰를 모두 resolve해야 Merge 할 수 있습니다.
