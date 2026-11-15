# 코드 구조

코드는 `app/src/main/java/com/jij6303/sidecoach/` 아래에 있고, **패키지 이름만 보고 책임을 알 수 있게** 나뉘어 있다.

## 1. 패키지와 책임

```
sidecoach/
├─ data/        SDK 연결
├─ state/       상태 분석·전처리
├─ feature/     기능별 구현
│  ├─ coach/      실시간 코칭 + 상황 예고
│  ├─ parking/    주차 코치
│  ├─ report/     주행 기록과 성적표
│  └─ tips/       차량 기능 도움말
├─ app/         진입점
├─ voice/       공통: 음성 출력
└─ ui/          공통: 테마, 여러 화면이 쓰는 타일
```

| 패키지 | 책임 | 주요 클래스 |
|---|---|---|
| `data/vehicle` | 차량 신호 구독 | `VehicleDataSource`(인터페이스), `PleosVehicleDataSource`(조향·깜빡이·브레이크), `AaosPropertyDataSource`(기어·차속), `FakeVehicleDataSource` |
| `data/navi` | 내비 경로 구독 | `NaviEventSource`(인터페이스), `PleosNaviEventSource`(NaviHelper), `FakeRouteNaviEventSource`(시연 경로), `RoutingNaviEventSource`(둘 중 선택), `TbtRouteTracker`(구간 거리 → 남은 거리) |
| `data/ux` | 주행 규제 구독 | `UxRestrictionSource`(인터페이스), `AaosUxRestrictionSource`, `FakeUxRestrictionSource` |
| `state` | 신호를 판단하기 좋은 형태로 묶기 | `DrivingSnapshot`(한 시점의 차량 상태), `DrivingStateRepository`(신호 → 스냅샷 흐름), `UxModeController`(규제 + 기어 → 화면 모드), `lane/CurrentLaneTracker`(지금 차로 추적) |
| `feature/coach` | 무엇을 언제 말할지 | `detect/*Detector`(코칭 판정), `situation/SituationPreviewEngine`(상황 예고), `CoachingArbiter`(우선순위·간격 중재), `CoachingPipeline`(전체 흐름), `VoiceCoach`(메시지 → 음성), `lane/LaneInput`(차로 입력), `ui/DrivingScreen`. 음성 문구는 `CoachMessage.kt`의 `MessageCatalog`, 녹음 클립은 `res/raw/` |
| `feature/parking` | 주차 절차 코치 | `ParkingProcedure`(단계 정의), `ParkingCoachEngine`(단계 진행 판정), `ParkingCoachController`, `ParkingSettings`, `ui/ParkingPanel` |
| `feature/report` | 주행 기록과 성적표 | `trip/TripRecorder`·`TripSession`(주행 시작·종료, 통계), `ReportGenerator`(점수·과제), `session/SessionStore`(세션 저장·복원), `ui/ReportScreen` |
| `feature/tips` | 차량 기능 도움말 | `CarTip`(도움말 목록), `CarTipSpeaker`, `ui/CarTipsScreen` |
| `app` | 앱이 시작되는 곳 | `SideCoachApp`(Application), `MainActivity`(화면 전환), `CoachingService`(Foreground Service), `di/ServiceLocator`(객체 생성·연결), `gleo/`(Gleo 명령 처리, 명령 이름은 `assets/schema.json`과 같아야 함), `dashboard/`(정차 화면) |
| `voice` | 소리 내기 | `VoiceOutput`(인터페이스), `ClipVoiceOutput`(녹음 클립 우선), `AndroidTtsVoiceOutput`, `AudioFocusController` |
| `ui` | 공통 화면 요소 | `theme/`(색·글꼴), `components/`(타일·칩) |

**의존 방향**: `app` → `feature` → `state` → `data`. 공통 요소는 `voice`, `ui`에 둔다.

`feature`끼리의 연결은 두 가지다.
- 모든 기능이 `feature/coach`의 `CoachMessage`로 말한다. 주차 안내, 성적표 요약, 도움말 답변도 같은 메시지 형식으로 음성 출력에 들어간다.
- `CoachingPipeline`이 주행 기록(`feature/report`의 `TripRecorder`)을 함께 돌린다. 코칭 판정과 통계가 같은 스냅샷을 같은 순서로 봐야 하기 때문이다.

## 2. 데이터 흐름

```
 차량 신호 (Pleos SDK, AAOS)        내비 경로 (NaviHelper / 시연 경로)
          │ data/vehicle                    │ data/navi
          ▼                                 │
  DrivingStateRepository ── DrivingSnapshot ┤
          │ state                           │
          ▼                                 ▼
  CoachingPipeline ─ 디텍터 5종 ─┐   SituationPreviewEngine
          │ feature/coach        ▼          │
          │                CoachingArbiter ◀┘
          │                      │ CoachMessage
          ├─▶ TripRecorder ─▶ ReportGenerator (feature/report)
          ▼                      ▼
     SessionStore            VoiceCoach ─▶ VoiceOutput (voice)
```

1. `data`가 SDK에서 신호를 받아 Flow로 내보낸다.
2. `DrivingStateRepository`가 신호를 합쳐 `DrivingSnapshot` 하나로 만든다.
3. `CoachingPipeline`이 스냅샷마다 디텍터를 돌리고, 내비 이벤트는 상황 예고로 바꾼다.
4. `CoachingArbiter`가 지금 말할 메시지 하나를 고른다.
5. `VoiceCoach`가 오디오 포커스를 잡고 녹음 클립(없으면 TTS)으로 말한다.
6. `TripRecorder`가 주행 통계를 쌓고, P에 들어가면 `ReportGenerator`가 성적표를 만든다.

주차 코치(`ParkingCoachController`)는 같은 스냅샷 흐름을 따로 구독해 R 기어일 때만 동작한다.

## 3. 주요 설계

- **판정 로직은 순수 Kotlin**: 디텍터, 중재자, 성적표, 주차 엔진은 Android에 의존하지 않는다. 시간은 스냅샷의 timestamp로 받아서 JVM 단위 테스트로 검증한다 (`app/src/test`).
- **데이터 소스는 인터페이스 + Fake**: 모든 SDK 연결은 인터페이스 뒤에 있고 Fake 구현이 있다. `ServiceLocator.init(useFake = true)`면 차량 없이 화면과 로직을 확인할 수 있다.
- **구독은 cold Flow**: collect하면 구독하고 취소하면 해제한다.
- **한 흐름으로 처리**: 스냅샷과 내비 이벤트를 `merge`해 순서대로 처리하므로 디텍터·중재자에 동시 접근이 없다.
- **음성 대기열은 최신 1개**: 오래된 메시지(4초 이상)는 버린다.
- **화면 모드는 규제 + 기어로 결정**: 주행 중(코칭 카드만) / 정차 / 주차(전체 대시보드).
- **세션 복원**: 코칭 중 5초마다 세션 상태를 저장하고, 프로세스가 다시 만들어지면 이어간다.
