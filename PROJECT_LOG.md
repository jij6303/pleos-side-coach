# pleos-side-coach

## 📌 프로젝트 개요
- 목적: (작성 예정)
- 시작일: 2026-11-09
- 기술 스택: Android (Kotlin, Jetpack Compose, Material3), Gradle Kotlin DSL + Version Catalog, minSdk 28 / targetSdk 37, Pleos Nexus 저장소

---

## 🗂️ 코드 구조

```
pleos-side-coach/
├── CLAUDE.md               # Claude Code 진입점, 작업 규칙 로드
├── .claude/rules/rules.md  # 작업 규칙 (워크플로우, Git 컨벤션, 문서화 규칙)
├── github/                 # GitHub App 봇 스크립트 (이슈·브랜치·PR·리뷰)
├── PROJECT_LOG.md          # 개발 일지
├── docs/                   # 기획(concept)·설계(dev-architecture)·Pleos 가이드 요약
├── app/                    # Android 앱 모듈 (Compose, 패키지 com.jij6303.sidecoach)
│   └── src/main/java/com/jij6303/sidecoach/
│       ├── data/vehicle/   # VehicleDataSource 인터페이스 + Pleos SDK·AAOS·Fake 구현
│       ├── domain/         # DrivingSnapshot, DrivingStateRepository (순수 Kotlin)
│       ├── di/             # ServiceLocator (수동 DI, Fake/Real 전환)
│       └── ui/             # MainActivity, 신호 디버그 화면, 테마
├── gradle/                 # Version Catalog(libs.versions.toml), Gradle wrapper
├── settings.gradle.kts     # 모듈·저장소 설정 (Pleos Nexus 포함)
├── build.gradle.kts        # 루트 빌드 설정
└── .gitignore              # 자격증명·빌드 산출물·개인 IDE 설정·서명 키 제외
```

- 왜 이 구조인가: claude-setup 템플릿을 그대로 사용해 다른 레포와 같은 작업 규칙·봇을 공유하기 위함

**주요 함수 목록**
| 함수 | 위치 | 한 줄 설명 |
|------|------|-----------|
| get_token() | github/auth.py | GitHub App JWT로 installation token 발급 |
| get_owner_repo() | github/auth.py | git remote origin에서 owner/repo 파싱 |

---

## ✅ 기능 목록

| 기능 | 설명 | 상태 |
|------|------|------|
| claude-setup 배포 (#1) | 작업 규칙·GitHub 봇·개발 일지 템플릿 추가 | ✅ 완료 |
| Android 프로젝트 뼈대 (#3) | Android Studio 기본 Compose 프로젝트 추가 | ✅ 완료 |
| 차량 신호 데이터 소스 (#7) | 패키지 정정, Vehicle SDK·AAOS 신호 구독, 신호 디버그 화면 | ✅ 완료 |
| 디텍터·코칭 중재 (#9) | 깜빡이 누락·급제동 판정, 중재자, 주행 통계 | 🔄 리뷰 중 |

---

## 📋 개발 일지

### #1 — claude-setup 초기 설정 배포 (2026-11-09)

**왜 이 작업을 했나**
> 새 레포에서도 Claude Code가 같은 워크플로우(이슈 → 브랜치 → PR)와 문서화 규칙을 따르도록 하기 위해

**뭘 했나**
> claude-setup의 `deploy.py`로 CLAUDE.md, .claude/rules/rules.md, github/ 봇 스크립트, PROJECT_LOG.md를 복사하고 .gitignore에 github_bot.json을 추가

**어떤 구조를 선택했고 왜**
> 규칙은 `.claude/rules/`에 두고 CLAUDE.md에서 불러오는 구조. 진입점과 규칙 본문을 분리해 템플릿 갱신 시 규칙 파일만 교체하면 되도록 함

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 파일을 수동 복사할 수도 있지만 deploy.py를 쓰면 .gitignore 처리까지 일관되게 적용되고 누락이 없음

**배운 것 / 몰랐던 것**
> 빈 레포에는 base 브랜치가 없어 봇의 create_branch가 동작하지 않으므로, main에 초기 커밋을 먼저 push해야 함
> 봇 스크립트를 실행하면 github/__pycache__가 생기므로 .gitignore에 넣어야 함

**에러 & 해결**
> 에러: `Author identity unknown` (커밋 실패)
> 원인: 레포에 git user.name/email 미설정
> 해결: 레포 로컬 설정으로 user.name/email 지정

---

### #3 — Android 프로젝트 뼈대 추가 (2026-11-09)

**왜 이 작업을 했나**
> 기능 개발을 시작하기 전에 Android Studio가 생성한 기본 프로젝트를 레포에 올려 이후 작업의 기준점을 만들기 위해

**뭘 했나**
> Gradle 설정·wrapper, app 모듈(MainActivity, Compose 테마, 리소스, 기본 테스트), 공유용 `.idea` 설정을 커밋하고 `.gitignore`에 Android/Gradle·개인 IDE 파일·서명 키 항목을 추가

**어떤 구조를 선택했고 왜**
> Android Studio 기본 단일 모듈(`:app`) 구조 + Version Catalog 유지. 아직 기능이 없으므로 모듈 분리는 필요할 때 진행

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> `.idea`를 통째로 제외할 수도 있지만, 컴파일러·Gradle 설정 등 공유 가능한 파일은 커밋하고 `workspace.xml`, `deviceManager.xml` 등 개인 환경 파일만 제외해 팀원 간 IDE 설정을 맞춤

**배운 것 / 몰랐던 것**
> Windows에서 커밋하면 `gradlew` 실행 권한이 빠지므로 `git update-index --chmod=+x gradlew`로 지정해야 함
> 이전 PR(#2)이 머지되지 않은 상태에서 main 기준 브랜치를 만들면 충돌하므로 선행 PR을 먼저 머지함

---

### #7 — 프로젝트 골격 및 차량 신호 데이터 소스 (2026-11-09)

**왜 이 작업을 했나**
> 코칭 판정(깜빡이 누락, 급제동)의 입력이 되는 차량 신호를 먼저 안정적으로 받아야 하고, 설계 문서의 확인 목록 5-1(AAOS 프로퍼티를 우리 앱에서 읽을 수 있는지)을 검증해야 했기 때문

**뭘 했나**
> 패키지를 고정값 `com.jij6303.sidecoach`로 정정. `VehicleDataSource` 인터페이스와 Pleos SDK(조향·깜빡이·브레이크), AAOS(기어·차속·비상등·주차 브레이크), Fake 구현을 만들고, `DrivingStateRepository`가 최신 스냅샷과 10초 이력을 유지하도록 함. 신호 디버그 화면으로 에뮬레이터에서 값 수신을 확인

**어떤 구조를 선택했고 왜**
> 데이터 소스는 cold `callbackFlow`로 만들어 collect 시작이 곧 구독, 취소가 곧 해제가 되게 함. 등록·해제 짝이 어긋날 여지가 줄어듦. 신호 모델(`VehicleSignal`)은 SDK 타입을 감싸 순수 Kotlin으로 두어 도메인을 JVM 테스트로 검증할 수 있게 함

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> SDK 시그니처는 문서 스켈레톤 대신 Gradle 캐시의 `Vehicle-2.3.2-sources.jar`와 공식 샘플 앱에서 직접 확인함. 문서에는 `initialize(onComplete, onFailed)`로 적혀 있었지만 실제로는 반환값 없는 `initialize()`이고, 연결 전 등록 요청은 SDK가 대기열로 처리함

**배운 것 / 몰랐던 것**
> `CAR_SPEED`는 dangerous 권한이고 위치 권한 그룹으로 묶여 "위치 정보 접근" 팝업이 뜸
> `HAZARD_LIGHTS_STATE`에 필요한 `CAR_EXTERIOR_LIGHTS`는 signature|privileged라 서드파티 앱은 비상등을 읽을 수 없음 → 설계의 비상등 경로 재검토 필요
> 에뮬레이터는 user 빌드라 `cmd car_service inject-vhal-event`를 쓸 수 없음. 값 변경 테스트는 Virtual Console로 해야 함

**에러 & 해결**
> 에러: 비상등 `registerCallback=false`
> 원인: `CAR_EXTERIOR_LIGHTS` 권한이 signature|privileged라 승인 불가
> 해결: 권한 선언에서 제외하고 화면에 "수신 불가: 권한 없음"으로 표시. 대체 경로는 후속 확인

---

### #9 — 깜빡이 누락·급제동 디텍터와 코칭 중재자 (2026-11-09)

**왜 이 작업을 했나**
> 앱의 핵심인 "실시간 조작 피드백"을 만들기 위해. 차량 신호(#7)를 받아 언제 무엇을 말할지 판정하는 단계

**뭘 했나**
> `BlinkerOmissionDetector`(S자 조향 + 깜빡이 확인), `HardBrakeDetector`(1초 감속도 + 브레이크), `CoachingArbiter`(우선순위·간격·TTL·상한), `TripRecorder`(깜빡이 사용률·급제동 횟수), `CoachingPipeline`으로 연결. 디버그 화면에 최근 코칭과 통계 표시. 단위 테스트 32개

**어떤 구조를 선택했고 왜**
> 판정 로직은 전부 `domain/` 순수 Kotlin이고 시간을 인자(스냅샷 timestamp)로 받아 JVM 테스트에서 시간을 마음대로 조작할 수 있게 함. 디텍터는 `StateFlow`가 아니라 합쳐지지 않는 `updates`(SharedFlow)를 구독해 조향 중간값을 놓치지 않게 함

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 깜빡이 확인을 10초 이력 버퍼에서 찾을 수도 있었지만, "조향 시작 3초 전"까지 거슬러 가면 버퍼 범위를 넘을 수 있어 디텍터가 방향별 마지막 꺼진 시각을 직접 기록하게 함
> 급제동의 저속 제외는 문서대로 현재 속도에 걸면 정지까지 가는 급제동(콘솔 45 → 0)이 안 잡혀 감속 시작 속도 기준으로 바꿈. 차속 이벤트가 값이 바뀔 때만 올 수 있어 계단형으로 보고 1초 전 속도를 추정

**배운 것 / 몰랐던 것**
> Virtual Console 조향 슬라이더 값과 Pleos SDK 값은 부호가 반대 (콘솔 −20 → SDK +20). 콘솔 기준으로 테스트를 안내하면 방향이 뒤집힘
> 콘솔 조향은 ±540°까지 들어옴 (SDK capability는 −470 ~ 500)
> 사용자가 콘솔을 조작하고 Claude가 스크린샷·logcat으로 확인하는 방식으로 실차 없이 시나리오 테스트 가능

**에러 & 해결**
> 에러: 오른쪽 깜빡이를 켜고 차선을 바꿨는데 누락 경고
> 원인: 콘솔 슬라이더 부호가 SDK와 반대라 실제로는 왼쪽 차선 변경으로 입력됨
> 해결: 로직은 정상. 콘솔에서는 반대 부호로 조작하도록 테스트 절차와 문서에 기록

---

## 🐛 에러 모음

| 에러 | 원인 | 해결 |
|------|------|------|
| Author identity unknown | git 사용자 정보 미설정 | 레포 로컬 `git config user.name/email` 설정 |
| 빈 레포에서 브랜치 생성 불가 | 원격에 base 브랜치(main) 없음 | main에 초기 커밋 push 후 브랜치 생성 |
| __pycache__ 커밋됨 | .gitignore에 미등록 | .gitignore에 `__pycache__/` 추가 후 인덱스에서 제거 |
| 비상등 registerCallback=false | CAR_EXTERIOR_LIGHTS가 signature\|privileged | 권한 선언 제외, 수신 불가로 표시 |
| 깜빡이를 켰는데 누락 경고 | Virtual Console 조향 부호가 SDK와 반대 | 콘솔에서는 반대 부호로 조작 (로직 정상) |
