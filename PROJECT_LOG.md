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
│       ├── domain/         # DrivingSnapshot, DrivingStateRepository, 디텍터·중재자·주행(trip/)·성적표(report/)·상황 예고(situation/)·튜토리얼(tutorial/) (순수 Kotlin)
│       ├── gleo/           # Gleo 음성 명령: 파싱·처리·응답 (schema.json은 assets/)
│       ├── di/             # ServiceLocator (수동 DI, Fake/Real 전환)
│       ├── data/navi/      # NaviEventSource, 시연용 가상 경로(FakeRouteNaviEventSource)
│       ├── data/ux/        # UxRestrictionSource (CarUxRestrictions + Fake)
│       ├── service/        # CoachingService (Foreground Service)
│       ├── voice/          # VoiceOutput(클립·Android TTS), AudioFocusController, VoiceCoach
│       └── ui/             # MainActivity, UxModeController, 대시보드(dashboard/), 주행 화면(driving/), 성적표(report/), 튜토리얼(tutorial/), 테마(theme/)
├── scripts/                # 음성 클립 생성 스크립트
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
| 디텍터·코칭 중재 (#9) | 깜빡이 누락·급제동 판정, 중재자, 주행 통계 | ✅ 완료 |
| 앱 이름 변경 (#11) | 옆자리 쌤 → 초보운전탈출 | ✅ 완료 |
| 대시보드 UI (#13) | 코칭 카드·차량 상태·주행 통계 타일, 앱 전용 테마 | ✅ 완료 |
| 음성 코칭 출력 (#15) | 오디오 포커스, 녹음 클립 + Android TTS fallback | ✅ 완료 |
| 코칭 서비스·주행 화면 (#17) | Foreground Service, 주행 규제별 화면 전환, 주행 화면 | ✅ 완료 |
| 성적표 (#19) | 주행 단위 판정, 점수·등급·다음 연습 과제, 성적표 화면·요약 음성 | ✅ 완료 |
| 상황 예고 (#21) | 가상 경로(Fake 내비) + 300~150m 예고, 상황별 음성 | ✅ 완료 |
| 튜토리얼 (#23) | 내 차 익히기: 깜빡이 좌·우·브레이크 미션, 조작 자동 확인 | ✅ 완료 |
| Gleo 연동 (#25) | schema.json + 코칭 시작·튜토리얼 activity 액션, 응답 브로드캐스트 | 🔄 리뷰 중 |

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

### #13 — 대시보드 UI 개선 (2026-11-09)

**왜 이 작업을 했나**
> 신호 확인 화면이 디버그용 목록이라 투박하고, 차량 화면(160dpi, 앱 창 약 1680×1210dp)에서 글자가 너무 작았기 때문

**뭘 했나**
> 앱 전용 테마(파랑 기본 + 신호 색: 깜빡이 주황·급제동 빨강·칭찬 초록, 라이트·다크)와 차량용 글자 크기를 만들고, 화면을 코칭 카드 + 차량 상태 타일(차속, PRND, 깜빡이 화살표, 조향 막대, 브레이크·주차 브레이크·비상등) + 주행 통계 타일로 재구성

**어떤 구조를 선택했고 왜**
> `ui/dashboard/`에 화면(`DashboardScreen`)과 타일 컴포넌트(`DashboardTiles`)를 분리. 너비 840dp 이상이면 2열, 미만이면 1열로 바꿔 세로·가로 창 모두 대응. 신호 색은 `LocalSignalColors`로 테마에 넣어 다크 모드에서도 의미가 같게 유지

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> Material You 동적 색상을 쓰면 배경화면에 따라 색이 바뀌어 "빨강 = 급제동" 같은 의미가 흔들려서 끔. 아이콘 라이브러리 대신 깜빡이 화살표·조향 막대를 Canvas로 직접 그려 의존성을 늘리지 않음. 애니메이션은 넣지 않음 (주행 중 금지)

**배운 것 / 몰랐던 것**
> Pleos 에뮬레이터는 160dpi라 1dp = 1px. 휴대폰 기준 글자 크기로는 차량 화면에서 너무 작음
> `adb shell cmd uimode night yes/no`로 다크 모드를 바로 확인할 수 있음

---

### #15 — TTS 음성 코칭 출력과 오디오 포커스 (2026-11-10)

**왜 이 작업을 했나**
> 코칭 메시지가 logcat에만 찍히고 있었음. 주행 중 앱은 음성 위주여야 하므로 실제 소리로 내야 했음

**뭘 했나**
> `VoiceOutput` 인터페이스, `AudioFocusController`(NAVIGATION_GUIDANCE, GAIN_TRANSIENT_MAY_DUCK, 손실 시 즉시 정지·재생 안 함, 상태 로그), `VoiceCoach`(포커스 실패·엔진 미준비 시 500ms마다 재시도, TTL 4초 지나면 폐기, 발화 중 들어온 메시지는 최신 1개만), `ClipVoiceOutput`(녹음 클립) + `AndroidTtsVoiceOutput`(fallback). 단위 테스트 7개 추가

**어떤 구조를 선택했고 왜**
> `VoiceOutput.speak()`를 끝날 때까지 기다리는 suspend 함수로 만들어, 포커스 요청 → 발화 → 반환 순서가 `try/finally` 한 곳에 모이게 함. `VoiceCoach`는 Android 의존이 없어 가상 시간으로 JVM 테스트 가능

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 에뮬레이터에 표준 TTS 엔진이 없어서 Pleos 내부 음성 서비스를 직접 호출하는 방법도 있었지만, 문서화되지 않은 API라 쓰지 않음. TTS 엔진 APK를 설치하는 방법은 심사 환경에서 소리가 안 나서 제외. 코칭 문구가 정해져 있으니 녹음 클립이 가장 확실함

**배운 것 / 몰랐던 것**
> Pleos 에뮬레이터의 유일한 TTS 서비스(`ai.pleos.playground.caas`)는 `CATEGORY_DEFAULT`가 없어 `MATCH_DEFAULT_ONLY` 조회(프레임워크 `TtsEngines`도 같음)에 안 잡힘
> Android 11+는 `<queries>`에 `TTS_SERVICE`를 넣어야 엔진이 보임
> `TextToSpeech` 연결이 실패하면 init 콜백이 생성자 안에서 동기로 불림
> `runTest`의 `advanceUntilIdle()`은 `backgroundScope` 작업을 기다리지 않음 → `runCurrent()` 사용
> PowerShell 5.1은 BOM 없는 UTF-8 스크립트의 한글을 깨뜨림

**에러 & 해결**
> 에러: 앱 시작 직후 크래시 `NullPointerException: TextToSpeech.shutdown() on a null object reference`
> 원인: 엔진 연결 실패 시 init 콜백이 생성자 안에서 바로 불려, 아직 대입 전인 `tts` 필드를 건드림
> 해결: init 콜백 처리를 메인 루퍼에 `post`로 미룸

---

### #17 — 코칭 포그라운드 서비스와 주행 규제별 화면 전환 (2026-11-10)

**왜 이 작업을 했나**
> 지금까지는 화면이 보일 때만 코칭했음. 최상단 Back은 앱을 숨기기만 해야 하는데(Pleos 규칙), 숨기면 코칭이 멈췄음. 또 주행 중에는 화면을 최소화해야 함

**뭘 했나**
> `CoachingService`(specialUse FGS, 알림에 "코칭 끄기", START_STICKY)가 신호 구독·판정·음성의 수명을 맡도록 옮김. `AaosUxRestrictionSource`(CarUxRestrictionsManager)와 `UxModeController`(규제 + 기어 → DRIVING_VOICE / IDLE_LIMITED / PARKED_FULL), 주행 중 화면 `DrivingScreen`(큰 코칭 카드 1장 + 차속·기어) 추가. 단위 테스트 5개 추가

**어떤 구조를 선택했고 왜**
> 화면 모드는 `ServiceLocator.uiMode`(StateFlow, WhileSubscribed)로 두어 화면이 볼 때만 규제를 구독함. 규제 정보를 못 받으면 가장 엄격한 상태(주행 중)로 취급. 주행 화면의 코칭 문구는 10초가 지나면 내림. 메시지 시각과 스냅샷 시각이 같은 시계라 타이머 없이 비교

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> FGS 타입은 위치·미디어 재생 등과 맞지 않아 설계대로 specialUse를 씀(심사 수용 여부는 확인 필요). 서비스 종료 버튼을 화면에 둘 수도 있지만, 주행 중 화면에는 입력을 최소화해야 해서 알림으로 둠

**배운 것 / 몰랐던 것**
> 문서의 주행 규제 adb 명령(`hmg_car_service`)은 이 에뮬레이터에 없음. 대신 규제가 콘솔 기어로 바뀜(P면 해제, 그 외 기어면 DO 필요)
> `dumpsys car_service --services CarUxRestrictionsManagerService`로 주행 상태별 규제 설정과 변경 기록을 볼 수 있음

---

### #19 — 주행 후 성적표와 다음 연습 과제 (2026-11-10)

**왜 이 작업을 했나**
> 주행 중 코칭만으로는 초보가 무엇을 연습해야 할지 남지 않음. 주행이 끝나면 결과와 다음 과제 하나를 보여줘야 "선생님" 경험이 완성됨 (concept ⑤)

**뭘 했나**
> `TripSession`(기어로 주행 시작·종료 판정, 주행 시간·거리·최고 속도), `ReportGenerator`(점수·등급·원포인트 과제), `ReportScreen`(P단에서만 표시, "확인"·Back으로 닫기), 성적표 요약 음성 클립 3개. 새 주행이 시작되면 통계·발화 횟수·성적표 초기화. 단위 테스트 12개 추가

**어떤 구조를 선택했고 왜**
> 주행 판정은 기존 파이프라인의 스냅샷 흐름에 얹어 별도 구독을 만들지 않음. 성적표 요약 음성은 주행이 끝난 뒤라 다른 코칭과 겹치지 않으므로 중재자를 거치지 않고 바로 음성으로 보냄. Back은 성적표가 떠 있을 때만 켜지는 콜백을 따로 두어, 최상단 Back(moveTaskToBack) 규칙과 하위 화면 Back(닫기) 규칙을 분리

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 점수는 감점 방식(누락·급제동 1회당 −10)으로 단순하게 둠. 초보에게 "왜 이 점수인지"가 바로 보이도록 하기 위함. 원포인트 과제는 여러 개 대신 가장 급한 하나만 제안 (깜빡이 > 급제동 > 차선 변경 연습 > 칭찬)

**배운 것 / 몰랐던 것**
> 차속 이벤트는 값이 바뀔 때만 오므로 정속 주행 중에는 이벤트 간격이 길다. 처음에 "간격 5초 상한"을 두었다가 정속 주행 거리가 깎이는 문제를 테스트로 발견해 제거함
> PowerShell 스크립트를 Python으로 고칠 때 `utf-8`로 읽고 `utf-8-sig`로 쓰면 BOM이 두 번 붙어 첫 줄이 깨짐

**에러 & 해결**
> 에러: TripSessionTest에서 주행 시간 10초 기대, 실제 5초
> 원인: 이벤트 간격 상한(5초)이 정상 정속 구간까지 잘라냄
> 해결: 상한 제거. 서비스가 멈추면 주행도 초기화되므로 긴 공백이 생길 일이 없음

---

### #21 — 상황 예고 코칭 (Fake 내비 이벤트) (2026-11-10)

**왜 이 작업을 했나**
> 기획의 메인 기능. 회전교차로·어린이보호구역 등을 지나기 전에 강사처럼 미리 말해줘야 함. NaviHelper 실연동은 2차라 MVP는 시연용 이벤트로 보여줌

**뭘 했나**
> `NaviEventSource` 인터페이스, 시연용 `FakeRouteNaviEventSource`(출발하면 가상 경로 400m 회전교차로·1.1km 어린이보호구역·1.8km 비보호좌회전·2.6km 합류, 실제 차속을 적분해 남은 거리 이벤트), `SituationPreviewEngine`(300~150m에서 1회, 같은 지점 10분 내 반복 금지), 상황별 문구·음성 클립 4개, 대시보드 "시연 경로" 표시. 단위 테스트 10개 추가

**어떤 구조를 선택했고 왜**
> 파이프라인이 스냅샷과 내비 이벤트를 `merge`한 하나의 흐름으로 순서대로 처리. 두 코루틴이 중재자를 동시에 건드리지 않게 하기 위함. 실제 NaviHelper 구현은 `NaviEventSource`만 바꾸면 들어올 수 있음

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 시간 기반 스크립트(예: 10초 뒤 이벤트)로 만들 수도 있었지만, 콘솔 차속을 적분하는 가상 경로가 실제 운전과 같은 감각(빨리 달리면 빨리 다가옴)을 줘 시연이 자연스러움. 문구에는 거리를 넣지 않아 녹음 클립으로 재생 가능

**배운 것 / 몰랐던 것**
> Android 14의 variable update rate 때문에 같은 차속 값은 이벤트가 오지 않을 수 있어, 가상 경로는 1초 타이머로도 거리를 갱신함
> 에뮬레이터 실측: D 후 약 50 km/h 주행 시 11초 만에 회전교차로 예고, 이어서 어린이보호구역 예고

---

### #23 — 내 차 익히기 튜토리얼 (2026-11-10)

**왜 이 작업을 했나**
> "차량이 실제 조작을 확인해주는 연수"는 폰 앱으로는 불가능한, 데모 임팩트가 가장 큰 기능 (concept ③)

**뭘 했나**
> `TutorialMission`(왼쪽·오른쪽 깜빡이, 브레이크), `TutorialEngine`(순서 진행·조작 감지·건너뛰기), `TutorialController`(스냅샷 구독 수명·음성 메시지), `TutorialScreen`(현재 미션 카드 + 목록 ✓). 대시보드 '내 차 익히기' 버튼(P단만), Back으로 종료, P를 벗어나면 자동 종료. 음성 클립 7개, 단위 테스트 8개 추가

**어떤 구조를 선택했고 왜**
> 조건이 거짓인 것을 한 번 본 뒤 참이 되어야 완료로 침. 이미 켜져 있던 상태로 통과하면 "직접 해봤다"는 의미가 없어서. 완료 시 칭찬과 다음 지시를 한 문장("잘했어요! 다음은 …")으로 보냄. VoiceCoach 대기열은 최신 1개만 남기므로 두 문장을 연달아 보내면 칭찬이 버려지기 때문

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 와이퍼 미션은 SDK가 `pleos.car.permission.CAR_WINDOWS`를 요구하는데 승인 여부가 확정되지 않아 제외(사용자 결정). 새 권한 없이 이미 받는 신호만으로 미션 구성

**배운 것 / 몰랐던 것**
> Vehicle SDK 와이퍼 API: `getWiper().registerWiperState(WindowArea, WiperStateListener)`, 상태 `WiperState`(OFF/ON 등), 권한 `CAR_WINDOWS`(가이드 요약의 `READ_WINDSHIELD_WIPERS`와 다름)
> Virtual Console에서는 주차 브레이크(`PARKING_BRAKE_ON`) 조작 항목을 찾을 수 없음 → 에뮬레이터에서 완료할 수 없는 미션이라 뺌(사용자 결정)
> 튜토리얼 중 기어가 잠깐이라도 P를 벗어나면 설계대로 튜토리얼이 끝남

---

### #25 — Gleo 음성 명령 연동 (2026-11-10)

**왜 이 작업을 했나**
> 운전 중에는 화면을 만질 수 없으니 차량 음성 AI(Gleo)로 "코칭 시작해줘", "튜토리얼 열어줘"를 말할 수 있어야 함. 공모전 기술 활용성 평가 항목이기도 함

**뭘 했나**
> `assets/schema.json`(activity 액션 2개: START_DRIVING_COACH + experienceLevel, RUN_VEHICLE_TUTORIAL), 매니페스트 intent-filter, `GleoActionParser`(ACTION_PARAM JSON), `GleoActionHandler`(명령 처리), `GleoResponse`·`GleoCallbackSender`(APP_ACTION_RESPONSE 브로드캐스트). 숙련도는 중재자 레벨(깜빡이 경고 상한)에 적용. 단위 테스트 13개(schema 규칙 검사 포함)

**어떤 구조를 선택했고 왜**
> 명령 처리 로직은 Android 의존 없이 람다를 주입받아 JVM 테스트 가능. 튜토리얼 명령은 앱이 꺼진 상태에서 호출되면 기어 값이 아직 없으므로 최대 3초 기다린 뒤 P단 여부를 판단(응답 7초 제한 안). schema 테스트로 "선언한 액션은 모두 코드에서 처리"를 보장

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 설계의 service 액션(EXPLAIN_CURRENT_TRAFFIC_RULE)은 Receiver 수신 검증이 필요한 2차 범위라 schema에 넣지 않음. 선언만 하고 동작하지 않으면 심사·사용성 모두 문제. 튜토리얼 액션의 targetComponent 파라미터는 실제 미션(깜빡이·브레이크)과 맞지 않아 뺌

**배운 것 / 몰랐던 것**
> Gleo(GAA, `ai.umos.ambientai`)는 앱 설치 시 schema.json을 바로 읽음 (`[GAA] [Schema] Loaded schema.json from com.jij6303.sidecoach` 로그)
> 실제 음성 호출은 Gleo 측 검토 통과 후 활성화되므로 에뮬레이터에서는 `am start -a <ACTION> --es ACTION_PARAM '<json>'`로 시뮬레이션
> 테스트 컴파일 클래스패스에는 android.jar의 org.json이 먼저 잡혀 `keySet()` 같은 최신 메서드를 못 씀 → `keys()` 사용

---

## 🐛 에러 모음

| 에러 | 원인 | 해결 |
|------|------|------|
| Author identity unknown | git 사용자 정보 미설정 | 레포 로컬 `git config user.name/email` 설정 |
| 빈 레포에서 브랜치 생성 불가 | 원격에 base 브랜치(main) 없음 | main에 초기 커밋 push 후 브랜치 생성 |
| __pycache__ 커밋됨 | .gitignore에 미등록 | .gitignore에 `__pycache__/` 추가 후 인덱스에서 제거 |
| 비상등 registerCallback=false | CAR_EXTERIOR_LIGHTS가 signature\|privileged | 권한 선언 제외, 수신 불가로 표시 |
| 깜빡이를 켰는데 누락 경고 | Virtual Console 조향 부호가 SDK와 반대 | 콘솔에서는 반대 부호로 조작 (로직 정상) |
| TextToSpeech init status=-1 | 에뮬레이터에 표준 TTS 엔진 없음 (Pleos 서비스는 CATEGORY_DEFAULT 없음) | 녹음 클립 재생 + TTS fallback |
| 앱 시작 직후 NPE (TextToSpeech) | 연결 실패 시 init 콜백이 생성자 안에서 동기 호출 | 콜백 처리를 메인 루퍼에 post |
| `cmd hmg_car_service` 없음 | 에뮬레이터에 해당 서비스 없음 | 콘솔 기어로 주행 규제 전환 |
| 주행 시간이 절반으로 집계 | 이벤트 간격 상한이 정속 구간을 잘라냄 | 상한 제거 |
| ps1 첫 줄 주석이 명령으로 실행됨 | BOM이 두 번 붙음 | BOM 하나로 정리 |
