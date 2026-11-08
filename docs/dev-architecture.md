# 초보운전탈출 (Pleos Side-Coach) 상세 개발 구조 문서

> 대상: Android Studio + Kotlin / Pleos Connect (AAOS, Android 14) · 작성 2026-11-08
> 근거: 「옆자리 쌤 기획서」(현 초보운전탈출)와 Pleos Connect 개발 문서(document.pleos.ai) 조사 내용

## 0. 표기 규칙 (사실과 추측 구분)

| 표기 | 의미 |
|---|---|
| ✅ | Pleos 문서에서 **이름 또는 수치를 직접 확인**함 |
| 🔶 | **AAOS·Android 표준 API**. 일반적인 AAOS 지식으로 작성했고, Pleos 문서에서 따로 확인하지 않음 |
| ❓ | **확인 필요**. 이름은 확인했지만 시그니처나 필드를 모르거나, 존재 여부 자체를 모름 |
| 💭 | **설계 가정**. 임계값처럼 이 문서에서 정한 값. 테스트하면서 조정해야 함 |

> 코드 블록은 **구조를 보여주는 스켈레톤**이다. ❓가 붙은 SDK 호출의 시그니처(인자, 콜백 타입)는 API Reference를 보고 맞춰야 한다.

## 0.1 에뮬레이터 실측 결과 (2026-11-09 업데이트) 🧪

Pleos Connect Emulator v2.1.0, Vehicle SDK 2.3.2, Vehicle Service 2.3.22, 공식 샘플 앱(pleos-sample-vehicle-app), Virtual Console로 직접 확인한 내용이다. **이 절의 내용은 본문의 ❓보다 우선한다.**

| 신호 | 실측 결과 | 설계 반영 |
|---|---|---|
| **조향각** | `checkSteeringWheelAngleCapabilities` → `CapabilityConfig(accessType=READ)`, `AreaBounds(minValue=-470.0, maxValue=500.0)`<br>`SteeringWheelAngleListener.onSteeringWheelAngleUpdated(Float)`<br>**왼쪽 = +, 오른쪽 = −**, 단위는 도(°) | 깜빡이 누락 판정: + 방향이면 LEFT, − 방향이면 RIGHT 깜빡이를 확인. 범위가 비대칭이라 임계값은 절댓값 기준<br>**⚠️ 실측(#9): Virtual Console 슬라이더 값은 SDK 값과 부호가 반대** (콘솔 −20 → SDK +20). 콘솔은 AAOS VHAL 규약(왼쪽 −)으로 보이며, 앱은 SDK 값 기준. 콘솔에서 −1080° 범위(±540)까지 들어옴 |
| **깜빡이** | `checkTurnSignalStateCapabilities` → `READ`<br>`TurnSignalStateListener.onTurnSignalStateUpdated(...)` → `OFF` / `LEFT` / `RIGHT`<br>콘솔 Blink와 Flash 구분 없이 **방향만** 전달됨 | 방향 enum만 사용 |
| **비상등** | 비상등을 켜면 TurnSignal이 **`OFF`로 보고됨**. Vehicle SDK Light 도메인(22개 기능)에 비상등 API 없음 | ~~AAOS `HAZARD_LIGHTS_STATE`로 읽음~~ → **❌ 실측(#7): 필요 권한 `CAR_EXTERIOR_LIGHTS`가 `signature\|privileged`라 서드파티 앱은 읽을 수 없음.** 비상등 감지 경로는 확인 필요. 그 전까지 깜빡이 누락 판정은 비상등 여부를 모르는 상태로 동작 |
| **브레이크** | `checkBrakePedalStatusCapabilities` → `READ`, 숫자 범위 없음<br>`BrakePedalStatusListener.onBrakePedalStatusUpdated(Boolean)` → 0%면 `false`, 50%면 `true`<br>콘솔은 위치(%)를 보내고 SDK는 "derived pressed status"(눌림 여부)만 전달 | 급제동 판정은 **차속 감속도가 주 판단**, 브레이크 `true`는 보조 조건. 페달 % 기준값 삭제 |
| **차속** | 콘솔에서 **기어 D + ACCEL 버튼 누르기**로 차속 증가, BRAKE로 감속 시뮬레이션 가능. 콘솔에 앞뒤 가속도(m/s²) 표시 | Fake 차속 주입 불필요. 앱에서는 AAOS `PERF_VEHICLE_SPEED`로 읽음 🔶(우리 앱에서 확인) |
| **기어** | 샘플 README: "Gear is not part of this sample's catalog". 콘솔에 Gear Selection(P/R/N/D) 있음 | AAOS `GEAR_SELECTION` 사용 🔶 |
| **경고등(Telltales)** | 샘플 앱 Safety 도메인(18개 기능)에 **없음**. 샘플은 private·hidden API를 제외하므로 공개 API가 아닌 것으로 판단. 콘솔에는 Telltales(Parking Brake, Seat Belt 등)가 있지만 계기판 **경고등 표시**를 바꾸는 기능임 | 텔테일 API는 사용하지 않음. 주차 브레이크는 AAOS `PARKING_BRAKE_ON` 🔶 |
| **안전벨트** | `checkSeatBuckledCapabilities` → `READ_WRITE` + SeatArea 목록. 단, **읽기 API 없음("No read API")**. `Seat Belt Not Buckled Warning` 기능은 존재. **콘솔에 벨트 체결 시뮬레이션 항목 없음**(belt·buckle 검색 결과 없음) | 에뮬레이터 데모에서는 벨트 미션 제외. 실차용으로 `registerSeatBeltNotBuckledWarning` 경로를 유지 |
| **와이퍼** | 콘솔에 Windshield Wiper Switch(Front/Rear, Off/Auto/Low/High), SDK에 Wiper 도메인(2개 기능) | 튜토리얼 미션으로 채택(SDK 메서드명 확인 필요) |
| **시스템 UI** | 깜빡이를 켜면 **시스템 측후방 카메라 팝업**이 앱 화면 위에 뜸 | 주행 중 화면이 가려질 수 있음 → 음성 중심 코칭 설계를 유지 |

### SDK 제공 상태 (Pleos Playground, 2026-11-09)
| SDK | 상태 | 의미 | 대응 |
|---|---|---|---|
| Vehicle State, NaviHelper | **사용 가능** | 신청 없이 바로 사용 | 그대로 사용 |
| TextToSpeech, SpeechToText, FusedLocation, Vehicle Control | **준비 중 (Coming Soon)** | API 제공 준비 중이라 **신청 자체가 불가** | TTS는 **Android 표준 `android.speech.tts.TextToSpeech`를 기본 구현**으로 하고 Pleos TTS는 제공되면 교체. STT·위치는 2차로 미룸. Vehicle Control은 사용 안 함 |

### Gradle
- `ai.pleos.playground:Vehicle:2.3.1`은 저장소에서 찾지 못해 빌드가 실패함. 버전을 수정한 뒤 빌드 성공. **샘플 기준은 2.3.2**이고, 실제 쓴 버전은 `libs.versions.toml`에서 확인.
- AGP 업그레이드 안내는 무시(샘플 조합 AGP 8.8.2 유지).

---

## 1. 전체 아키텍처

### 1.1 레이어 구성

```
┌──────────────────────────────────────────────────────────────┐
│ UI 레이어 (Jetpack Compose)                                    │
│  MainActivity ─ DrivingScreen / TutorialScreen / ReportScreen │
│  UxModeController가 화면 모드를 결정 (주행: 음성 / 정차: 전체 UI)   │
└───────────────▲──────────────────────────────▲───────────────┘
                │ StateFlow<UiState>            │ Gleo Intent
┌───────────────┴──────────────┐  ┌────────────┴───────────────┐
│ 서비스 레이어                 │  │ Gleo 연동                    │
│  CoachingService (Foreground)│  │  GleoActionReceiver(service) │
│   └ CoachingPipeline 호스팅   │  │  MainActivity(activity)      │
└───────────────▲──────────────┘  │  GleoCallbackSender          │
                │                  └─────────────────────────────┘
┌───────────────┴──────────────────────────────────────────────┐
│ 도메인 레이어 (순수 Kotlin, 단위 테스트 대상)                       │
│  DrivingStateRepository → DrivingSnapshot (StateFlow)          │
│  Detectors: BlinkerOmission / HardBrake / Speeding             │
│  SituationPreviewEngine (내비 이벤트 + 상황 DB)                   │
│  CoachingArbiter (우선순위, 쿨다운, 빈도 제한) → CoachMessage        │
│  TripRecorder → ReportGenerator                                │
└───────────────▲──────────────────────────────▲───────────────┘
                │ Flow<Signal>                  │ Flow<NaviEvent>
┌───────────────┴──────────────┐  ┌────────────┴───────────────┐
│ 데이터 레이어 (인터페이스 + 구현 교체 가능)                          │
│  VehicleDataSource           │  │  NaviEventSource             │
│   ├ PleosVehicleDataSource   │  │   ├ PleosNaviEventSource     │
│   ├ AaosPropertyDataSource   │  │   └ FakeNaviEventSource      │
│   └ FakeVehicleDataSource    │  │  LocationSource / SituationDb│
│  UxRestrictionSource (AAOS)  │  │  VoiceOutput / VoiceInput     │
└──────────────────────────────┘  └─────────────────────────────┘
```

### 1.2 데이터 흐름

```
[Vehicle SDK / AAOS 콜백]──callbackFlow──▶ VehicleDataSource.signals
                                              │
[NaviHelper 콜백]──callbackFlow──▶ NaviEventSource.events
                                              ▼
                         DrivingStateRepository (최신 스냅샷 + 짧은 시계열 버퍼)
                                              │
               ┌──────────────┬───────────────┼────────────────┐
               ▼              ▼               ▼                ▼
      BlinkerOmission   HardBrake     Speeding     SituationPreviewEngine
               └──────────────┴───────┬───────┴────────────────┘
                                      ▼
                             CoachingArbiter ──▶ VoiceOutput (TTS + 오디오 포커스)
                                      │
                                      └──▶ TripRecorder ──(P단)──▶ ReportGenerator ──▶ ReportScreen
```

### 1.3 핵심 설계 결정

1. **데이터 소스를 인터페이스로 추상화하고 Fake 구현을 둔다.**
   - Virtual Console에서 차속을 바꿀 수 있는지 아직 확인되지 않았다(❓).
   - 내비 이벤트 필드도 확인되지 않았다(❓).
   - 그래서 테스트와 데모용 `Fake*` 구현으로 갈아 끼울 수 있게 만든다.
2. **코칭은 Foreground Service에서 돈다.**
   - Back 키를 누르면 앱은 숨겨지지만(`moveTaskToBack`) 코칭은 계속되어야 하기 때문이다.
3. **도메인 레이어는 Android 의존성 없이 작성한다.**
   - 판정 로직을 JVM 단위 테스트로 검증할 수 있게 하려는 목적이다.
4. **MVP는 단일 `:app` 모듈 + 패키지 분리로 간다.**
   - 마감(11/15)을 생각한 선택이다. 2차 이후 필요하면 모듈로 분리한다.

---

## 2. 패키지·클래스 구조

패키지명은 예시이고 💭, 실제 값은 Pleos My Project에 등록한 값으로 쓴다.

```
com.sidecoach
├─ SideCoachApp.kt                  Application, ServiceLocator 초기화
├─ di/ServiceLocator.kt             수동 DI (Fake/Real 구현 전환 플래그)
├─ data/
│  ├─ vehicle/
│  │  ├─ VehicleDataSource.kt       interface: signals: Flow<VehicleSignal>, start(), stop()
│  │  ├─ VehicleSignal.kt           sealed: SteeringAngle, TurnSignal, BrakePedal, Gear, Speed, Telltale, SeatBelt
│  │  ├─ PleosVehicleDataSource.kt  Vehicle SDK 구독 (조향, 깜빡이, 브레이크, Safety)
│  │  ├─ AaosPropertyDataSource.kt  CarPropertyManager 구독 (기어, 차속)
│  │  ├─ CompositeVehicleDataSource.kt  위 두 소스를 merge
│  │  └─ FakeVehicleDataSource.kt   디버그 패널, 스크립트 재생
│  ├─ navi/
│  │  ├─ NaviEventSource.kt         interface: events: Flow<NaviEvent>
│  │  ├─ NaviEvent.kt               sealed: Tbt, CautionPoint, SpeedLimit, CameraAlert, RouteState
│  │  ├─ PleosNaviEventSource.kt    NaviHelper + NaviHelperEventListener
│  │  └─ FakeNaviEventSource.kt
│  ├─ location/
│  │  ├─ LocationSource.kt          FusedLocation 래퍼 (isSupported 분기)
│  │  └─ SituationDb.kt             assets/situations.json (회전교차로, 비보호 등 좌표 DB)
│  ├─ ux/UxRestrictionSource.kt     CarUxRestrictionsManager → Flow<UxState>
│  └─ store/
│     ├─ SessionStore.kt            DataStore: 세션 활성, 레벨, 쿨다운 상태 (프로세스 재생성 복원용)
│     └─ TripDao.kt                 Room: 주행 기록, 이벤트 로그
├─ domain/
│  ├─ DrivingSnapshot.kt            data class: speedKmh, gear, steeringDeg, turnSignal, brakePct, ts
│  ├─ DrivingStateRepository.kt     스냅샷 StateFlow + 링버퍼(최근 10초)
│  ├─ detect/
│  │  ├─ Detector.kt                interface: onSnapshot(s): DetectionEvent?
│  │  ├─ BlinkerOmissionDetector.kt
│  │  ├─ HardBrakeDetector.kt
│  │  └─ SpeedingDetector.kt
│  ├─ situation/
│  │  ├─ SituationType.kt           ROUNDABOUT, MERGE, SCHOOL_ZONE, UNPROTECTED_LEFT, SPEED_CAMERA
│  │  └─ SituationPreviewEngine.kt  NaviEvent + 위치 + DB → 예고 트리거
│  ├─ coach/
│  │  ├─ CoachMessage.kt            (type, priority, text, dedupeKey)
│  │  ├─ CoachingArbiter.kt         우선순위 큐, 최소 간격, 유형별 상한, 숙련도 정책
│  │  └─ MessageCatalog.kt          레벨·상황별 문구 (한국어)
│  ├─ trip/TripRecorder.kt          이벤트 집계 (깜빡이 사용률, 급제동, 과속)
│  └─ report/ReportGenerator.kt     성적표, 원포인트 과제 생성
├─ voice/
│  ├─ VoiceOutput.kt                interface: speak(msg), stop()
│  ├─ PleosTtsVoiceOutput.kt        TextToSpeech SDK (ON_DEVICE)
│  ├─ AudioFocusController.kt       NAVIGATION 컨텍스트, MAY_DUCK
│  └─ PleosSttVoiceInput.kt         SpeechToText SDK ("지금 어떻게 해?")
├─ service/
│  ├─ CoachingService.kt            Foreground Service, 파이프라인 수명 관리
│  └─ CoachingPipeline.kt           소스 → 리포지토리 → 디텍터 → 중재자 → 음성 연결
├─ gleo/
│  ├─ GleoContract.kt               액션명, ACTION_PARAM 키, 응답 액션 상수
│  ├─ GleoActionParser.kt           ACTION_PARAM JSON 파싱 (runCatching)
│  ├─ GleoActionReceiver.kt         component=service 액션 수신 (BroadcastReceiver)
│  └─ GleoCallbackSender.kt         APP_ACTION_RESPONSE 브로드캐스트
├─ tutorial/
│  ├─ TutorialMission.kt            HAZARD_LIGHT, SEAT_BELT, PARKING_BRAKE, WIPER, DEFROST
│  └─ TutorialEngine.kt             미션별 완료 판정 (신호 매칭)
└─ ui/
   ├─ MainActivity.kt               Compose 호스트, Gleo activity 액션 처리, Back 처리
   ├─ UxModeController.kt           UxState + 기어 → UiMode(DRIVING_VOICE / PARKED_FULL)
   ├─ driving/DrivingScreen.kt      핵심 카드 1장, 애니메이션 없음
   ├─ tutorial/TutorialScreen.kt
   ├─ report/ReportScreen.kt
   └─ debug/DebugPanel.kt           (debug 빌드 전용) Fake 신호와 이벤트 주입
```

---

## 3. 사용 API 명세

### 3.1 Gradle 설정

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")   // ✅
    }
}
// app/build.gradle.kts
android {
    compileSdk = 35            // ✅ 샘플 앱 기준
    defaultConfig { minSdk = 28; targetSdk = 35 }   // ✅ 제품 앱은 API 28 이상
    useLibrary("android.car")  // 🔶 AAOS Car API (CarPropertyManager, CarUxRestrictionsManager)
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")   // ✅ 2.3.1은 저장소에서 못 찾음(실측). 샘플 기준 2.3.2
    // NaviHelper, TextToSpeech, SpeechToText, FusedLocation 좌표 ❓ 각 SDK의 importing-sdk 페이지에서 확인
}
```

- 툴체인은 ✅ 샘플 앱 기준으로 JDK 17, AGP 8.8.2, Kotlin 2.0.0, Gradle wrapper 8.10.2.

### 3.2 API · 권한 · 구독 시점

| 기능 | API | 권한 | 구독 / 해제 시점 | 상태 |
|---|---|---|---|---|
| Vehicle 연결 | `Vehicle.initialize()` / `release()`, 결과는 `onComplete`/`onFailed` 비동기 람다 | – | Service `onCreate` / `onDestroy`. **콜백이 끝나기 전에 release 금지** | ✅ |
| 조향각 | `SteeringWheel`: `checkSteeringWheelAngleCapabilities`, `getCurrentSteeringWheelAngle`, `registerSteeringWheelAngle`, `unregisterSteeringWheelAngle` | ❓ 권한 표에 SteeringWheel 항목 없음 (후보: `READ_CAR_STEERING`) | 코칭 세션 시작 / 종료 | ✅ API, ❓ 권한·단위·주기 |
| 깜빡이·비상등 | `TurnSignal`: `checkTurnSignalStateCapabilities`, `getCurrentTurnSignalState`, `registerTurnSignalState`, `unregisterTurnSignalState` | `pleos.car.permission.CAR_LIGHTS` | 세션 시작 / 종료, 튜토리얼 화면 | ✅ API, ❓ 상태 enum 값 |
| 브레이크 | `Brake`: `checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus`, `registerBrakePedalStatus`, `unregisterBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` | 세션 시작 / 종료 | ✅ API, ❓ 값 형태(boolean 또는 %) |
| 경고등·벨트 | `Safety`: `getTelltalesInfo`, `registerTelltales`, `unregisterTelltales`, `hasSeatBeltNotBuckledWarning`, `registerSeatBeltNotBuckledWarning` | `pleos.car.permission.CAR_SAFETY` | 튜토리얼 화면 진입 / 이탈 | ✅ |
| 와이퍼 | `Wiper` 도메인 | ❓ | 튜토리얼 | ❓ 메서드·필드 |
| 김서림 제거 | `HVAC` 도메인 (디프로스터 상태) | ❓ | 튜토리얼 | ❓ 메서드·필드 |
| 기어 | `CarPropertyManager` + `VehiclePropertyIds.GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` 🔶 | 서비스 수명 동안 상시 (P단 감지용) | 🔶 (Pleos 정책상 표준 데이터는 AAOS API 권장 ✅) |
| 차속 | `CarPropertyManager` + `VehiclePropertyIds.PERF_VEHICLE_SPEED` (m/s) | `android.car.permission.CAR_SPEED` 🔶 | 세션 동안 | ✅ FAQ에서 클러스터와 같은 값이라고 안내, 🔶 권한 |
| 주행 규제 | `CarUxRestrictionsManager.registerListener()`, `getCurrentCarUxRestrictions()`, `isRequiresDistractionOptimization()`, `getActiveRestrictions()` | – | 앱 프로세스 수명 동안 | ✅ |
| 내비 이벤트 | `NaviHelper.initialize()` → `addListener(NaviHelperEventListener)` → `removeListener()` → `release()` | ❓ (declaring-permissions 페이지 미확인) | 세션 시작 / 종료 | ✅ 흐름 |
| └ 콜백 | `onTBTInfo`, `onCautionPointInfo`, `onCurrentRoadSpeedLimit`, `onCameraAlert`, `onDrivingInfo`, `onRouteStateInfo`, `onNavigationStatus` | | | ✅ 이름, ❓ 데이터 클래스(`TBTInfo`, `CautionPoint` 등) 필드 |
| 위치 | `FusedLocation.isSupported()` → `initialize()` → `registerFusedLocationCallback()` / `unregisterFusedLocationCallback()` → `release()` | `pleos.car.permission.FUSED_LOCATION` | 세션 동안 (지원되는 차량만) | ✅, ❓ `LocationData` 필드명 |
| TTS | `ai.pleos.playground.tts.TextToSpeech(ctx, Mode.ON_DEVICE)` → `initialize()` → `addEventListener()` → `setLocale()` → `speak()` / `stop()` → `removeEventListener()` → `release()` | ❓ | 서비스 수명 | ✅ 흐름, ❓ `speak` 시그니처, 포커스 처리 주체 |
| STT | `ai.pleos.playground.stt.SpeechToText(ctx, Mode.ON_DEVICE)` → `initialize()` → `addListener(ResultListener)` → `request()` / `stop()` → `release()` | ❓ (마이크 권한 `RECORD_AUDIO` 🔶 추정) | 질문 버튼이나 Gleo 호출 시 단발 | ✅ 흐름 |

> ⚠️ 차량 권한은 **App Market과 사전 협의해서 승인받은 범위만** 선언해야 한다(✅). 승인되지 않은 권한을 선언하면 심사 탈락이다. 공모전 단계에서는 에뮬레이터로 시연하고, 출시 전에 권한 협의를 진행한다.

### 3.3 VehicleDataSource 스켈레톤

```kotlin
class PleosVehicleDataSource(private val ctx: Context) : VehicleDataSource {
    private val vehicle = Vehicle(ctx)          // ❓ 생성자 형태 확인
    override val signals: Flow<VehicleSignal> = callbackFlow {
        vehicle.initialize(
            onComplete = {
                val steering = vehicle.getSteeringWheel()   // ❓ getter 이름 (문서 패턴: getDoor(), getBrake())
                steering.checkSteeringWheelAngleCapabilities(onComplete = { supported ->
                    if (supported) steering.registerSteeringWheelAngle(/* listener → trySend(SteeringAngle(..)) */)
                }, onFailed = { /* 미지원 → 해당 디텍터 비활성 */ })
                // TurnSignal, Brake도 같은 패턴: check → register
            },
            onFailed = { close(it) }
        )
        awaitClose {
            // 같은 listener 객체·area로 unregister ✅ 후, 진행 중인 콜백이 끝난 뒤 release
            vehicle.release()
        }
    }
}
```

주의(✅ 샘플 가이드):
- `SDK initialized`만으로 서비스 연결을 판단하지 않는다.
- `Unsupported`는 "실제 미지원"과 "확인 실패"를 모두 포함할 수 있다.
- `null`이나 `unavailable`이 권한 누락을 뜻하는 것은 아니다.
- 특정 area에서 `area ID not supported`가 날 수 있다.

---

## 4. 핵심 로직 설계

### 4.0 공통: DrivingSnapshot과 버퍼

- `DrivingStateRepository`는 신호가 들어올 때마다 스냅샷을 갱신한다. **이벤트 기반으로 동작하고 폴링은 하지 않는다.**
- 최근 10초를 링버퍼(💭)에 보관해서 디텍터가 시간 창 계산에 쓴다.
- 차속은 `PERF_VEHICLE_SPEED`(m/s) × 3.6으로 km/h로 바꾼다.
- 감속도는 차속 시계열의 차분으로 계산한다.

### 4.1 깜빡이 누락 판정 (BlinkerOmissionDetector)

**전제와 한계**
- 조향각만으로 "차선 변경"을 확정할 수는 없다. 차선 정보가 없기 때문이다.
- 그래서 **차선 변경과 비슷한 조향 패턴**을 잡고, 커브나 교차로 회전은 걸러내는 **휴리스틱**으로 접근한다.
- 임계값은 전부 💭이고, 실차나 에뮬레이터 로그로 튜닝해야 한다. 조향각 단위와 부호 규약은 ❓ 확인 필요.

```
상수 (💭)
  V_MIN        = 30 km/h        // 기획서 기준. 저속 회전과 주차 상황 제외
  THETA_ON     = 12°            // 조향 시작 판단 (핸들 각도 기준)
  THETA_OFF    = 4°             // 중립 복귀 판단
  T_MANEUVER   = 2.0 ~ 8.0 s    // 차선 변경 1회의 시간 창
  T_SIGNAL_PRE = 3.0 s          // 조향 시작 전 깜빡이를 켰다고 인정하는 시간
  CURVE_GUARD  = 200 m          // TBT 회전·분기 안내 지점 전후 억제 거리
  // 부호 규약 ✅ 실측: 조향각 + = 왼쪽, − = 오른쪽 → dir1 > 0 이면 LEFT 깜빡이를 기대

상태: IDLE → SWING_1 → SWING_2 → (판정) → IDLE

onSnapshot(s):
  if s.speedKmh < V_MIN or s.gear != D: reset(); return
  if s.hazardOn: reset(); return                                          // ✅ 실측: 비상등 중엔 TurnSignal=OFF로 보고됨 → 오경고 방지
  if situationEngine.isNearTurnOrCurve(CURVE_GUARD): reset(); return   // TBT 기반 억제
  when state:
    IDLE:
      if |s.steeringDeg| >= THETA_ON:
        dir1 = sign(s.steeringDeg); tStart = s.ts; state = SWING_1
    SWING_1:
      if sign(s.steeringDeg) == -dir1 and |s.steeringDeg| >= THETA_ON:   // 반대 방향으로 되돌리는 조향 (S자 패턴)
        state = SWING_2
      if s.ts - tStart > T_MANEUVER.max: reset()                         // 너무 길면 커브로 간주
    SWING_2:
      if |s.steeringDeg| <= THETA_OFF:
        dur = s.ts - tStart
        if dur in T_MANEUVER:
          signalled = buffer.anyTurnSignal(dir = dir1, from = tStart - T_SIGNAL_PRE, to = tStart + 1s)
          tripRecorder.laneChange(signalled)
          if !signalled: emit(BLINKER_OMISSION, dir1)
        reset()
```

- 깜빡이 사용률 = 깜빡이를 켠 차선 변경 / 감지된 차선 변경 전체. 성적표에 쓴다.
- 오탐을 줄이는 장치:
  - TBT 억제
  - 저속 제외
  - 시간 창 제한
  - S자 패턴 요구
- 추후 FusedLocation의 진행 방향(heading) 변화량을 같이 보면 커브와 차선 변경을 구분하는 정확도가 올라간다(💭).

### 4.2 급제동 판정 (HardBrakeDetector)

✅ 실측 결과 브레이크 값은 **Boolean(눌림 여부)**뿐이다. 그래서 **차속 기반 감속도를 주 판정**으로 쓰고, 브레이크 `true`는 "브레이크로 인한 감속인지" 확인하는 보조 조건으로 쓴다.

```
상수 (💭)
  DECEL_TH     = 10 km/h/s   (≈ 0.28 g)   // 일반 텔레매틱스 급감속 기준 범위 안에서 정한 가정값
  MIN_SPEED    = 15 km/h                  // 저속 정지 직전의 제동은 제외
  COOLDOWN     = 10 s

onSnapshot(s):
  if s.speedKmh < MIN_SPEED: return
  decel = buffer.speedDrop(window = 1.0 s)          // km/h/s
  if (decel >= DECEL_TH) and buffer.brakePressedWithin(1.0 s):
     if now - lastFired > COOLDOWN:
        tripRecorder.hardBrake(location = lastLocation)
        emit(HARD_BRAKE)
```

### 4.3 과속 (SpeedingDetector)

```
limit = navi.lastSpeedLimit (onCurrentRoadSpeedLimit)    // ❓ 필드·단위
if limit != null and s.speedKmh > limit + 5 (💭 허용 오차) for >= 3 s (💭):
   emit(SPEEDING, limit)    // 같은 제한속도 구간에서는 1회만
```

### 4.4 상황 예고 트리거 (SituationPreviewEngine)

입력은 두 경로다.
1. NaviHelper 이벤트: `onTBTInfo`, `onCautionPointInfo`, `onCameraAlert`, `onCurrentRoadSpeedLimit`
2. `SituationDb`(assets/situations.json: `{id, type, lat, lon, headingRange}`, 💭 공공 지도 데이터에서 직접 구축) + FusedLocation 위치

```
on NaviEvent.Tbt(t):                        // ❓ TBTInfo에 안내 유형과 남은 거리가 있는지 확인
   type = mapTbtToSituation(t)               // 회전교차로, 합류(진입 램프) 등. 매핑이 안 되면 무시
   if type != null: candidate(type, distance = t.remainDistance, key = t.id or (type, pointLatLon))
on NaviEvent.CautionPoint(c):               // ❓ 어린이보호구역 등 유형 필드 확인
   candidate(map(c.type), c.distance, key = c.id)
on location(loc) every update:              // 내비 이벤트로 알 수 없는 상황 보완 (비보호 좌회전 등)
   for p in situationDb.nearby(loc, 400 m):
      if headingMatches(loc.heading, p.headingRange):
         candidate(p.type, haversine(loc, p), key = p.id)

candidate(type, d, key):
   if key in announced (TTL 10 min): return          // 중복 방지
   if d <= 300 m and d >= 150 m (💭):                // 200~300m 전 브리핑이 목표
      arbiter.submit(CoachMessage(PREVIEW, type, priority = HIGH, dedupeKey = key))
      announced.add(key)
```

- `isNearTurnOrCurve(r)`: 최근 TBT 안내 지점까지의 거리가 r 이하이면 true. BlinkerOmission 억제에 쓴다.
- **"지금 어떻게 해?"(STT나 Gleo로 들어온 질문)**: 가장 최근 candidate나 현재 진행 중인 상황을 골라 설명 문구를 즉시 낭독한다.

### 4.5 코칭 중재 (CoachingArbiter)

여러 디텍터가 동시에 말하려고 할 때 무엇을 말할지 정하는 역할이다.

```
우선순위: HARD_BRAKE(안전) > PREVIEW(상황 예고) > SPEEDING > BLINKER_OMISSION > PRAISE
규칙 (💭):
  - 발화 사이 최소 간격 6초 (예외: HIGH 우선순위는 3초)
  - 유형별 주행당 상한: 초급 BLINKER 5회 / 중급 2회, PRAISE 주행당 3회
  - 대기열 TTL 4초: 시간이 지난 교정 메시지는 버림 (상황 예고는 거리 조건으로 다시 판단)
  - Gleo 음성 세션 중이거나 포커스를 잃은 상태에서는 보류
  - 칭찬 트리거: 깜빡이를 켜고 차선 변경을 3회 연속 완료했을 때
```

### 4.6 튜토리얼 판정 (TutorialEngine)

| 미션 | 완료 조건 | 상태 (실측 반영) |
|---|---|---|
| TURN_SIGNAL | TurnSignal이 지시한 방향(`LEFT`/`RIGHT`)으로 바뀜 | ✅ 실측 확인. MVP 데모 1순위 |
| WIPER | Wiper 상태 변화 (콘솔 Windshield Wiper Switch로 테스트 가능) | 🔶 SDK 메서드명 확인 필요. MVP 데모 2순위 |
| HAZARD_LIGHT | AAOS `HAZARD_LIGHTS_STATE` = ON (TurnSignal로는 확인 불가 ✅ 실측) | ❌ 서드파티 앱 권한으로 읽기 불가(#7 실측). 대체 경로 확인 전까지 보류 |
| PARKING_BRAKE | AAOS `PARKING_BRAKE_ON` = true (텔테일 API는 비공개 ✅ 실측) | ✅ 읽기 확인(#7, `CAR_POWERTRAIN`) |
| SEAT_BELT | `registerSeatBeltNotBuckledWarning` 경고 해제 | ❌ 에뮬레이터 콘솔에서 시뮬레이션 불가 → 실차용으로만 유지 |
| DEFROST | HVAC 디프로스터 ON | ❓ 2차 |

- 화면 진입 시 `check*Capabilities`로 지원 여부를 확인하고, **지원되지 않는 미션은 목록에서 숨긴다.**

---

## 5. 주행 규제 상태별 UI·음성 모드

### 5.1 모드 결정

```kotlin
// UxModeController
val uiMode = combine(uxRestrictionSource.state, vehicle.gear) { ux, gear ->
    when {
        !ux.requiresDO && gear == Gear.P -> UiMode.PARKED_FULL      // 성적표, 튜토리얼, 설정
        !ux.requiresDO                   -> UiMode.IDLE_LIMITED     // 정차했지만 P단이 아닐 때 (신호 대기 등): 리포트 없이 간단 UI
        else                             -> UiMode.DRIVING_VOICE    // 주행 규제 중
    }
}
```

- 활성 규제 플래그(✅): NO_DIALPAD=1, NO_FILTERING=2, LIMIT_STRING_LENGTH=4, NO_KEYBOARD=8, NO_VIDEO=16, LIMIT_CONTENT=32, NO_SETUP=64

### 5.2 모드별 동작

| 항목 | DRIVING_VOICE | IDLE_LIMITED | PARKED_FULL |
|---|---|---|---|
| 화면 | 핵심 카드 1장 (예: "300m 회전교차로 · 회전 차량 우선"). 큰 글자, 한 줄 문구 | 현재 상태 카드 | 전체 UI |
| 애니메이션 | **없음** (NO_VIDEO를 받으면 음성 파형 애니메이션도 정지) | 최소 | 허용 |
| 입력 | 터치 버튼은 "질문하기" 1개만. 키보드 없음 (NO_KEYBOARD) | 버튼 | 터치 전체 |
| 텍스트 | LIMIT_STRING_LENGTH를 받으면 줄임 문구 사용. 모든 문구 80자, 3줄 이내 ✅ | 같음 | 일반 |
| 리포트, 튜토리얼, 설정 | **진입 차단** (NO_SETUP, LIMIT_CONTENT) | 차단 | 허용 |
| 음성 코칭 | 전체 | 전체 | 튜토리얼 안내만 |

- 규제가 걸리는 순간 리포트나 튜토리얼 화면이 열려 있으면 즉시 DrivingScreen으로 전환한다.
- 규제가 풀려도 화면이 자동으로 바뀌지는 않는다. 단, P단에 들어가면 성적표 카드를 띄운다.
- 화면 응답은 0.25초 이내, 대비는 4.5:1 이상, 글자는 12sp 이상(✅ 디자인 가이드)을 지킨다.

---

## 6. 오디오 포커스·TTS 재생 정책

### 6.1 포커스 요청 (AudioFocusController)

```kotlin
val attrs = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)   // 🔶 → Pleos 매핑상 NAVIGATION 컨텍스트 ✅
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)   // ✅ 가이드 권장
    .setAudioAttributes(attrs)
    .setOnAudioFocusChangeListener(::onFocusChange)
    .build()
```

| 이벤트 | 처리 |
|---|---|
| 요청 성공 | 발화 시작 |
| 요청 실패 (예: CALL 중이라 R) | 메시지 보류. 교정 메시지는 TTL이 지나면 버리고, 상황 예고는 거리 조건으로 다시 판단 |
| `AUDIOFOCUS_LOSS` | 즉시 `tts.stop()`, 대기열 비우기 |
| `AUDIOFOCUS_LOSS_TRANSIENT` (Gleo 호출 등) | 즉시 정지. 해당 메시지는 다시 재생하지 않음 |
| `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` | 우리 앱은 음성 안내라서 duck 대신 짧게 일시정지 (💭) |
| 발화 완료 | `abandonAudioFocusRequest(req)` |

- 포커스 상태 변화는 로그로 남긴다(✅ 가이드 필수 로그 항목): context, gain, 결과.

### 6.2 TTS 정책

- **⚠️ 2026-11-09 기준 Pleos TTS SDK는 "준비 중"이라 신청이 불가하다.** 그래서 MVP의 `VoiceOutput` 기본 구현은 **Android 표준 `android.speech.tts.TextToSpeech`**(`AndroidTtsVoiceOutput`)로 한다. 선행 무음은 표준 API의 `playSilentUtterance(150ms)` 🔶로 넣는다. Pleos TTS가 제공되면 `PleosTtsVoiceOutput`으로 교체한다. 인터페이스가 분리돼 있어서 코칭 로직은 바뀌지 않는다.
- Pleos TTS를 쓰게 되면 **모드는 `Mode.ON_DEVICE`를 쓴다**(✅). 네트워크가 필요 없고 앱 등록도 필요 없다. 주행 중 통신이 불안정해도 동작한다.
- **선행 무음 150~200ms** ✅ 가이드
  - ❓ Pleos TTS SDK가 직접 재생하는 구조라면 PCM 앞에 무음을 덧붙이는 방식을 쓸 수 없을 수 있다. SDK에 무음 옵션이 있는지 확인해야 한다.
  - 💭 대안: 포커스를 얻고 출력 경로가 준비된 뒤 `speak()`를 호출한다. 문구 맨 앞에 짧은 쉼표나 공백을 두는 방식도 효과를 확인해볼 만하다.
- ❓ **포커스를 누가 잡는지**: SDK가 내부에서 오디오 포커스를 요청하는지 확인해야 한다. 중복 요청을 피해야 하기 때문이다. 확인 전까지는 앱이 직접 요청하는 구조로 구현하고 플래그로 끌 수 있게 둔다.
- 문장은 한 번에 1문장, 15어절 이내(💭)로 한다. 같은 문구를 연속으로 반복하지 않는다.
- Gleo Callback으로 응답할 때 앱이 직접 음성을 재생하면 `mode: "silent"`로 보내서 이중 낭독을 막는다(✅).

---

## 7. Gleo Interface

### 7.1 assets/schema.json (전문)

```json
{
  "packageName": "com.sidecoach",
  "appDescription": "A driving coach app that gives beginner drivers real-time voice guidance based on vehicle signals and upcoming road situations.",
  "version": "1.0.0",
  "actionCalls": [
    {
      "type": "function",
      "function": {
        "component": "activity",
        "name": "START_DRIVING_COACH",
        "description": "Starts a real-time voice coaching session for the driver at the selected experience level.",
        "parameters": {
          "type": "object",
          "properties": {
            "experienceLevel": {
              "type": "string",
              "description": "The driver's experience level for adjusting how often coaching is given.",
              "enum": ["BEGINNER", "INTERMEDIATE"]
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "component": "service",
        "name": "EXPLAIN_CURRENT_TRAFFIC_RULE",
        "description": "Explains the traffic rule and the recommended driver action for the current or upcoming road situation.",
        "parameters": {
          "type": "object",
          "properties": {},
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "component": "activity",
        "name": "RUN_VEHICLE_TUTORIAL",
        "description": "Opens an interactive tutorial that teaches the driver how to operate a vehicle control while parked.",
        "parameters": {
          "type": "object",
          "properties": {
            "targetComponent": {
              "type": "string",
              "description": "The vehicle control to practice in the tutorial.",
              "enum": ["HAZARD_LIGHT", "SEAT_BELT", "PARKING_BRAKE", "WIPER", "DEFROST"]
            }
          },
          "required": []
        }
      }
    }
  ]
}
```

준수 체크(✅):
- action 3개 이하
- 이름은 대문자와 밑줄
- description은 영어 1~2문장. 예시 없음, 다른 앱을 억제하는 문구 없음
- 타입은 string만 사용, enum은 문자열만
- `required`에는 필수 파라미터만 넣는다. 여기서는 전부 선택 파라미터라서 빈 배열이다.

확인 필요(❓):
- 샘플에 있는 `invokeWord` 필드는 명세에 없어서 넣지 않았다. 필요한지 확인해야 한다.
- `version`은 App Market에 등록한 버전과 맞춰야 한다.
- `parameters.properties`가 빈 객체여도 검토를 통과하는지 확인해야 한다.

### 7.2 Intent 수신

- Gleo 쪽 동작(✅)
  - `activity`: `setPackage` 후 `startActivity`
  - `service`: `setPackage` 후 `sendBroadcast`
- 파라미터는 extra `"ACTION_PARAM"`에 JSON 문자열로 들어온다.

```kotlin
object GleoContract {
    const val EXTRA_PARAM = "ACTION_PARAM"                                              // ✅
    const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE" // ✅
    const val START_DRIVING_COACH = "START_DRIVING_COACH"
    const val EXPLAIN_CURRENT_TRAFFIC_RULE = "EXPLAIN_CURRENT_TRAFFIC_RULE"
    const val RUN_VEHICLE_TUTORIAL = "RUN_VEHICLE_TUTORIAL"
}

// MainActivity: activity 컴포넌트 액션 처리
override fun onCreate(b: Bundle?) { super.onCreate(b); handleGleo(intent) }
override fun onNewIntent(i: Intent) { super.onNewIntent(i); handleGleo(i) }   // launchMode=singleTask

private fun handleGleo(i: Intent) {
    val params = GleoActionParser.parse(i.getStringExtra(GleoContract.EXTRA_PARAM)) // runCatching, 실패하면 빈 맵
    when (i.action) {
        GleoContract.START_DRIVING_COACH -> {
            if (uxMode.value == UiMode.DRIVING_VOICE && !coachingActive) { /* 주행 중에도 음성으로 시작 가능 */ }
            CoachingService.start(this, level = params["experienceLevel"] ?: "BEGINNER")
            GleoCallbackSender.success(this, i.action!!, "The driving coach session has started.",
                data = """[{"level":"${params["experienceLevel"] ?: "BEGINNER"}"}]""")
        }
        GleoContract.RUN_VEHICLE_TUTORIAL -> {
            if (uxMode.value != UiMode.PARKED_FULL) {
                GleoCallbackSender.fail(this, i.action!!, "The tutorial is available only while the vehicle is parked.")
            } else { /* 튜토리얼 화면 이동 */ GleoCallbackSender.success(this, i.action!!, "The tutorial has been opened.") }
        }
    }
}

// GleoActionReceiver: service 컴포넌트 액션 처리
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != GleoContract.EXPLAIN_CURRENT_TRAFFIC_RULE) return
        val pending = goAsync()                                  // 응답은 7초 안에 ✅
        CoroutineScope(Dispatchers.Default).launch {
            val situation = ServiceLocator.situationEngine.currentOrNext()
            if (situation == null) {
                GleoCallbackSender.success(ctx, i.action!!, "There is no special road situation ahead right now.")
            } else {
                ServiceLocator.voice.speak(MessageCatalog.explain(situation))   // 앱이 한국어로 상세 설명
                GleoCallbackSender.success(ctx, i.action!!, "The app is explaining the upcoming road situation.",
                    mode = "silent", data = """[{"situation":"${situation.type}"}]""")
            }
            pending.finish()
        }
    }
}
```

- `service` 액션을 받는 Receiver는 Manifest에 등록한다. Gleo가 `setPackage`로 대상을 지정해서 보내므로 manifest receiver로 받을 수 있다(🔶 Android 암시적 브로드캐스트 제한의 예외).
- ❓ 문서에는 service 방식의 Manifest 예시가 없다. 실제로 수신되는지 반드시 검증해야 한다.

### 7.3 Callback 전송

```kotlin
object GleoCallbackSender {
    fun send(ctx: Context, fn: String, status: String, message: String, mode: String = "voice", data: String? = null) {
        runCatching {
            val json = JSONObject().apply {
                put("functionName", fn)              // 호출된 name과 정확히 일치 ✅
                put("status", status)                // success | asking | fail ✅
                put("message", message)              // 영어 완전한 문장 ✅
                put("mode", mode)                    // voice | beep | silent ✅
                data?.let { put("data", it) }        // JSON 배열 문자열 (권장) ✅
            }
            val intent = Intent(GleoContract.ACTION_RESPONSE).apply {
                putExtra("packageName", ctx.packageName)   // ✅
                putExtra("response", json.toString())      // ✅
                // ❓ 수신 대상 패키지(setPackage) 지정 여부는 문서에 없음
            }
            ctx.sendBroadcast(intent)
        }
    }
    fun success(ctx: Context, fn: String, msg: String, mode: String = "voice", data: String? = null) = send(ctx, fn, "success", msg, mode, data)
    fun fail(ctx: Context, fn: String, msg: String) = send(ctx, fn, "fail", msg)
}
```

---

## 8. AndroidManifest.xml 핵심 설정

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.automotive" android:required="true" />  <!-- 🔶 AAOS 앱 -->

    <!-- Pleos 차량 권한: 실제 사용하는 것만 선언, App Market 승인 필요 ✅ -->
    <uses-permission android:name="pleos.car.permission.CAR_LIGHTS" />     <!-- TurnSignal ✅ -->
    <uses-permission android:name="pleos.car.permission.CAR_DRIVE" />      <!-- Brake ✅ -->
    <uses-permission android:name="pleos.car.permission.CAR_SAFETY" />     <!-- Safety ✅ -->
    <uses-permission android:name="pleos.car.permission.FUSED_LOCATION" /> <!-- FusedLocation ✅ -->
    <!-- ❓ SteeringWheel 각도 권한: 권한 표에 없음. 후보 pleos.car.permission.READ_CAR_STEERING -->

    <!-- AAOS 표준 🔶 -->
    <uses-permission android:name="android.car.permission.CAR_POWERTRAIN" />  <!-- GEAR_SELECTION, PARKING_BRAKE_ON -->
    <uses-permission android:name="android.car.permission.CAR_EXTERIOR_LIGHTS" />  <!-- HAZARD_LIGHTS_STATE -->
    <uses-permission android:name="android.car.permission.CAR_SPEED" />       <!-- PERF_VEHICLE_SPEED -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />        <!-- STT ❓ SDK 요구 확인 -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" /> <!-- 🔶 Android 14 FGS 타입. 심사 수용 여부 ❓ -->

    <application
        android:name=".SideCoachApp"
        android:label="@string/app_name">

        <!-- CCP(로터리) 미지원 선언 ✅ (User-installed 앱은 선택 사항. 필수 조작은 터치로 완결) -->
        <meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false" />

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <!-- 주행 중 사용을 위한 DO 선언 🔶 AAOS 표준 키. Pleos 문서는 "DO 선언"만 언급하고 키 이름은 없음 ❓ -->
            <meta-data android:name="distractionOptimized" android:value="true" />

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Gleo activity 액션: function마다 action filter ✅ -->
            <intent-filter>
                <action android:name="START_DRIVING_COACH" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter>
                <action android:name="RUN_VEHICLE_TUTORIAL" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- Gleo service 액션 (sendBroadcast) ❓ 문서 예시 없음, 수신 검증 필요 -->
        <receiver android:name=".gleo.GleoActionReceiver" android:exported="true">
            <intent-filter>
                <action android:name="EXPLAIN_CURRENT_TRAFFIC_RULE" />
            </intent-filter>
        </receiver>

        <service
            android:name=".service.CoachingService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                      android:value="realtime_driving_coaching" />
        </service>
    </application>
</manifest>
```

- `screenOrientation`은 지정하지 않는다. 기본 portrait이고, 사용자가 window 크기를 바꾸면 landscape로도 동작해야 한다(✅). 두 레이아웃을 모두 대응한다.
- 이 앱은 Music/Audio나 Navigation 카테고리가 아니다. 그래서 `appCategory="audio"`나 `APP_MAPS`는 선언하지 않는다. 카테고리 불일치는 즉시 반려 사유다(✅).

---

## 9. 생명주기 처리

| 상황 | 처리 | 근거 |
|---|---|---|
| **Back 키 (최상단 화면)** | `OnBackPressedDispatcher`에서 하위 화면이 없으면 `moveTaskToBack(true)`. **`finish()`나 `finishAffinity()`는 쓰지 않음**. 코칭은 Service에서 계속 | ✅ CCP 가이드: 미구현 시 반려 |
| **Back 키 (하위 화면)** | 이전 화면으로 이동하거나 팝업 닫기 | ✅ |
| **백그라운드 전환** | UI 갱신과 애니메이션 정지. 센서 구독은 Service가 유지하되, 튜토리얼 전용 구독(Safety 등)은 해제 | ✅ 성능 가이드 |
| **포그라운드 복귀** | `DrivingStateRepository`의 StateFlow로 즉시 최신 상태를 그림. 화면 크기와 방향에 맞춰 레이아웃 재구성 | ✅ |
| **프로세스 재생성** | `SessionStore`(DataStore)에 저장한 값으로 복원. 저장 항목: `sessionActive`, `level`, `tripId`, 유형별 발화 횟수. Service는 `START_STICKY` | ✅ 가이드: 상태 저장·복원 필수 |
| **세션 종료** | 기어 P 상태가 일정 시간(💭 60초) 이상 이어지거나 사용자가 종료하면, 성적표를 생성하고 Service를 stop | 💭 |
| **Suspend 진입 (IGN OFF 이후)** | ① TTS 즉시 정지 ② 오디오 포커스 반납 ③ 주행 로그 flush ④ 센서 구독 해제 | ✅ Suspend 가이드 |
| **Suspend 복귀** | 이전 오디오 상태를 자동으로 복원하지 않음. 포커스도 자동으로 다시 요청하지 않음. 세션 재개 여부는 사용자가 결정 | ✅ |
| **Suspend 감지 방법** | ❓ 가이드는 `onSuspendEntering()`과 `onResumeFromSuspend()`라는 예시 구조만 보여주고, 어떤 신호로 트리거하는지는 설명하지 않음. 💭 후보: AAOS `IGNITION_STATE` 프로퍼티 구독(CAR_POWERTRAIN). 가용 여부 확인 필요 | ❓ |
| **SDK 해제 순서** | 리스너 unregister(등록할 때와 같은 객체·area) → 진행 중인 비동기 콜백 완료 대기 → `release()` | ✅ |

리소스 목표(✅): 주행 중 CPU 5% 미만(비내비 앱 기준), RAM 400MB 이하. 이를 위해 다음을 지킨다.
- 폴링하지 않는다.
- 링버퍼 크기를 고정한다.
- Compose 재구성을 최소화한다. 상태 변화가 있을 때만 다시 그린다.

---

## 10. 에뮬레이터·Virtual Console 테스트 계획

### 10.1 환경

- Pleos Connect Emulator v2.1.0, API 34 시스템 이미지. AVD는 RAM 4GB, **Cold boot**(✅. Quick boot는 VHAL이 비활성화됨)
- Virtual Console 연결(✅): `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`
- 콘솔에서 바꿀 수 있는 값(✅):
  - 조향각 -540~540°(1° 단위)
  - 기어 P/R/N/D
  - 깜빡이 Off, 좌/우 Blink, 좌/우 Flash
  - 브레이크 0~100%
  - 텔테일 Off/On/Blink
  - 크루즈 상태와 목표 속도
- **차속**: ✅ 실측. 콘솔 메인 화면에서 **기어 D + ACCEL 버튼을 누르고 있으면** 차속이 오르고, BRAKE로 감속한다. 조향 슬라이더와 함께 쓰면 주행을 그대로 재현할 수 있다.
- 콘솔 Emulation Controls(48개 기능, 7개 도메인)에서 확인한 항목: Gear Selection, Brake Pedal(%), Turn Signal State, Hazard Lights, Telltales, Windshield Wiper Switch, Door Position/Lock, Seat Occupancy, Window, Cruise Control 등. **안전벨트 체결 항목은 없음.** 위쪽 검색창은 CarProperty 이름으로도 검색된다.
- **GPS 경로 재생은 문서에 없음** → 상황 예고는 `FakeNaviEventSource`로 이벤트를 주입해서 시연한다. 시연 영상에는 이 사실을 표기한다.
- 주행 규제 전환(✅):
  ```
  adb shell cmd hmg_car_service --service drivingstate driving_regulation on
  adb shell cmd hmg_car_service --service drivingstate driving_regulation off
  ```
- Gleo 액션 시뮬레이션(💭: 문서에 있는 방법은 아님, Intent 구조를 보고 추론함):
  ```
  adb shell am start -a START_DRIVING_COACH -n com.sidecoach/.ui.MainActivity --es ACTION_PARAM '{"experienceLevel":"BEGINNER"}'
  adb shell am broadcast -a EXPLAIN_CURRENT_TRAFFIC_RULE -p com.sidecoach
  ```
- 녹화(✅): `adb exec-out screenrecord --output-format=h264 - > demo.h264` → ffmpeg로 mp4 변환

### 10.2 테스트 케이스

| # | 시나리오 | 조작 | 기대 결과 |
|---|---|---|---|
| T1 | 세션 시작 | Gleo adb 명령 또는 앱 버튼 | Service 시작, 알림 표시, callback success |
| T2 | 깜빡이 누락 | 기어 D, 차속 50(주입), 조향 +20° → -20° → 0°, 깜빡이 Off | "깜빡이 먼저!" 음성 1회, 성적표 누락 +1 |
| T3 | 깜빡이 정상 | T2와 같은 조작에 깜빡이 우측 Blink를 먼저 켬 | 음성 없음, 사용률 증가, 3회 연속이면 칭찬 |
| T4 | 커브 억제 | Fake TBT 회전 안내 100m 앞 + 조향 | 경고 없음 |
| T5 | 저속 제외 | 차속 20 + 조향 패턴 | 경고 없음 |
| T6 | 급제동 | 차속 60 → 30 (1초), 브레이크 0 → 90% | "부드럽게…" 음성, 쿨다운 10초 안에는 재발화 없음 |
| T7 | 상황 예고 | Fake 회전교차로 이벤트 거리 300m → 100m | 300~150m 구간에서 1회만 발화 |
| T8 | 주행 규제 | driving_regulation on | DrivingScreen 전환, 리포트·튜토리얼 진입 차단, 애니메이션 정지 |
| T9 | 성적표 | 규제 off + 기어 P | 성적표 표시, 수치가 로그와 일치 |
| T10 | 튜토리얼 | P단, 미션 HAZARD → 콘솔에서 Flash | ✓ 완료 표시 |
| T11 | Back 키 | 최상단에서 Back | 앱이 숨겨지고 코칭 음성은 계속, 프로세스 유지 |
| T12 | 포커스 상실 | 코칭 발화 중 다른 앱이 포커스 요청 | 즉시 정지, 메시지 재생 안 함 |
| T13 | 프로세스 재생성 | `adb shell am kill com.sidecoach` (백그라운드 상태) | Service 재시작 후 세션 복원 |
| T14 | 폼 팩터 | FormFactorConsole로 portrait와 landscape 전환 | 잘림·겹침 없음 |
| T15 | Gleo service 액션 | `am broadcast EXPLAIN_…` | 상황 설명 음성, callback `silent` 전송 |

- 도메인 단위 테스트(JVM): 디텍터와 중재자는 **시계열 스크립트**(CSV: ts, speed, steering, signal, brake)를 넣어서 판정 결과를 검증한다.

---

## 11. 개발 일정 (1차 마감 11/15 기준)

### 11.1 MVP 범위 (11/15)

| 포함 | 제외 (2차, 11/23~12/02) |
|---|---|
| Vehicle 신호 구독 (조향, 깜빡이, 브레이크) + AAOS 기어·차속 | NaviHelper 실연동, 상황 DB 구축 |
| BlinkerOmission, HardBrake 디텍터 + 중재자 | FusedLocation 기반 상황 매칭 |
| TTS(ON_DEVICE) + 오디오 포커스 | STT 질의응답 |
| 주행 규제 UI 전환 | 모바일 리포트 |
| 튜토리얼 (깜빡이, 와이퍼 + AAOS 확인되면 비상등, 주차 브레이크) | 안전벨트(실차 전용), 김서림 미션 |
| TTS는 Android 표준 TextToSpeech | Pleos TTS 교체 (SDK 제공 후) |
| 성적표 (기본) | 숙련도 자동 조절 |
| 상황 예고는 **Fake 이벤트**로 시연 | |
| schema.json + Gleo activity 액션 1개 | service 액션 Receiver 실검증 |

### 11.2 일정

| 날짜 | 작업 | 완료 기준 |
|---|---|---|
| 11/9 (월) | Android Studio·에뮬레이터 셋업, Pleos 가입과 My Project 생성, 샘플 vehicle 앱 빌드·실행 | 샘플 앱이 콘솔 값을 읽음 |
| 11/10 (화) | 프로젝트 골격(패키지 구조, ServiceLocator), Vehicle·AAOS 데이터 소스, **❓ 항목 확인** (조향 권한·단위, 깜빡이 enum, 브레이크 값 형태, 차속 콘솔) | 신호 로그 출력 |
| 11/11 (수) | DrivingStateRepository, 두 디텍터, 중재자, 단위 테스트 | T2~T6 JVM 테스트 통과 |
| 11/12 (목) | TTS, 오디오 포커스, CoachingService, UxModeController, DrivingScreen | T1, T8, T11, T12 |
| 11/13 (금) | 튜토리얼, 성적표, Fake 상황 예고, schema.json, Gleo activity 액션 | T7, T9, T10 |
| 11/14 (토) | 통합 테스트, 시연 영상 녹화(시나리오 순서), README, release APK(`apk/팀명_앱이름_release.apk`) | 영상과 APK 준비 |
| 11/15 (일) | GitHub 정리, 기획서와 함께 제출 | 제출 완료 |

### 11.3 확인 목록 (설계 확정 조건)

1. ~~SteeringWheel 각도 API: 단위·부호~~ ✅ 완료 (Float, 왼쪽 +, -470~500). 남은 것: 필요 권한, 콜백 주기
2. ~~TurnSignal 상태 enum 값~~ ✅ 완료 (OFF/LEFT/RIGHT, 비상등은 OFF로 보고)
3. ~~BrakePedalStatus 값 형태~~ ✅ 완료 (Boolean)
4. ~~Virtual Console 차속 변경~~ ✅ 완료 (D + ACCEL)
5. NaviHelper Gradle 좌표와 권한 (TTS·STT·FusedLocation은 "준비 중" → Android 표준 API로 대체)
5-1. ~~우리 앱 첫 테스트: AAOS 프로퍼티 읽기~~ ✅ 완료(#7). `GEAR_SELECTION`·`PARKING_BRAKE_ON`은 `CAR_POWERTRAIN`(normal)으로, `PERF_VEHICLE_SPEED`는 `CAR_SPEED`(dangerous, 위치 권한 그룹) 런타임 승인 후 수신. **`HAZARD_LIGHTS_STATE`는 수신 불가**(`CAR_EXTERIOR_LIGHTS`가 signature|privileged). 남은 것: 비상등 대체 경로, 튜토리얼 HAZARD_LIGHT 미션 유지 여부
6. TTS SDK가 오디오 포커스를 직접 잡는지, 선행 무음 옵션이 있는지
7. `TBTInfo`, `CautionPoint`, `SpeedLimit`의 필드 구성 (상황 유형과 거리)
8. Gleo: service 액션 Receiver 수신 여부, Callback 대상 패키지, 에뮬레이터에 Gleo가 있는지
9. DO 선언 메타데이터 키 (AAOS 표준 `distractionOptimized`를 써도 되는지)
10. Suspend 감지 신호 (`IGNITION_STATE` 사용 가능 여부)
11. Wiper, HVAC 디프로스터 상태 API

> 1~4번 결과에 따라 4장 디텍터의 입력과 임계값이 바뀐다. 5~8번은 2차 범위를 확정하는 근거가 된다.

---

### 부록 A. 참고 문서

- Vehicle SDK intro / Sample App 가이드 / 권한 / Safety, TurnSignal, Brake, SteeringWheel 엔트리 (document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/…)
- NaviHelper event-listeners, TTS·STT·FusedLocation intro
- Gleo Interface: app-capability, action_callback, examples, deliver-schema
- 주행 안전 설계, 지역별 주행 규제, 주행 규제 개발 가이드, 오디오 포커스·Suspend, 음성 선행 무음, CCP, FAQ, ADB 명령, Virtual Console, 폼 팩터 테스트
- 성능·리소스 가이드, UI Design Guide
