# 초보운전탈출 (Pleos Side-Coach) 작업 정리

> 기간: 2026-11-09 ~ 2026-11-12 (4일, 커밋 45개, 이슈·PR #1 ~ #62)
> 공모전 1차 마감: 2026-11-15
> 상세 개발 일지는 `PROJECT_LOG.md`(#1~#39), 설계·판정 로직은 `docs/dev-architecture.md` 참고

---

## 1. 한눈에 보기

초보운전자 옆자리에 강사가 앉아 있는 것처럼, 차량 신호(조향·깜빡이·브레이크·기어·차속)를 읽어 **실시간 음성으로 코칭**하는 Pleos Connect(AAOS) 앱.

| 영역 | 현재 상태 |
|------|-----------|
| 실시간 코칭 | 깜빡이 누락·급제동·깜빡이 끄기 잊음·회전 중 과속·움직이는 중 기어 변경 + 칭찬 |
| 상황 예고 | 시연용 가상 경로로 회전교차로·어린이보호구역·비보호좌회전·합류 300~150m 전 예고 |
| 주차 코치 | 후진 주차 5단계, 평행 주차 9단계를 조향각·정지로 확인하며 음성 안내 |
| 성적표 | 주행 종료(P) 시 점수·등급·다음 연습 과제 1개 + 요약 음성 |
| 차량 기능 도움말 | 김 서림·내기/외기·에어컨·가속 모드·회생제동·페달 모드·차일드락 9종 |
| Gleo 음성 명령 | 코칭 시작(`START_DRIVING_COACH`), 차량 기능 질문(`ASK_CAR_TIP`) |
| 음성 | 녹음 클립 우선 재생 + Android TTS fallback, 자주 나오는 16개는 직접 녹음본 |
| 안정성 | Foreground Service, 주행 규제별 화면 전환, 프로세스 재생성 시 세션 복원 |
| 시연 | Virtual Console HTTP API를 조작하는 자동 시나리오 스크립트(약 171초) |

---

## 2. 날짜별 진행

### 11/09 — 기반 다지기
| # | 작업 |
|---|------|
| #1 | claude-setup 배포 (작업 규칙, GitHub 봇, 개발 일지) |
| #3 | Android 프로젝트 뼈대 (Compose, Version Catalog) |
| #5 | 프로젝트 규칙·설계 문서 추가 |
| #7 | 차량 신호 데이터 소스: Pleos SDK(조향·깜빡이·브레이크) + AAOS(기어·차속·비상등·주차 브레이크) + Fake, 신호 디버그 화면 |
| #9 | 깜빡이 누락·급제동 디텍터, 코칭 중재자(우선순위·간격·TTL·상한), 주행 통계 |
| #11 | 앱 이름 변경: 옆자리 쌤 → 초보운전탈출 |
| #13 | 대시보드 UI (코칭 카드 + 차량 상태 타일 + 통계 타일, 전용 테마) |

### 11/10 — 핵심 기능
| # | 작업 |
|---|------|
| #15 | 음성 출력: 오디오 포커스, 녹음 클립 + TTS fallback |
| #17 | 코칭 Foreground Service, CarUxRestrictions 기반 주행/정차 화면 전환 |
| #19 | 주행 후 성적표와 다음 연습 과제 |
| #21 | 상황 예고 코칭 (Fake 내비, 실제 차속 적분으로 남은 거리 계산) |
| #23 | 내 차 익히기 튜토리얼 (→ #35에서 제거) |
| #25 | Gleo 연동: schema.json, activity 액션, 응답 브로드캐스트 |
| #27 | 숙련도 구분 제거(교습 단일 모드), 깜빡이 카메라 팝업 회피 배치 |
| #29 | 차량 기능 도움말 9종 (Canvas로 버튼 그림 직접 그림, Gleo 음성 답변) |
| #31 | 주행 세션 저장·복원 (SharedPreferences + JSON, 5초 주기) |
| #33 | 앱 창 1개(841dp) 크기 대응 — 2열 기준 1200dp로 |

### 11/11 — 주차 코치, 디자인, 코칭 확장
| # | 작업 |
|---|------|
| #35 | 튜토리얼 제거 (서드파티로 확인 가능한 조작이 너무 적음) |
| #37 | 주차 절차 코치 (후진·평행, 조향각·정지로 단계 진행) |
| #39 | 직접 녹음한 음성 클립 1차 적용 (5개) |
| #41 | 주행 화면 재설계: 강사 캐릭터 얼굴 + 말풍선 코칭, 상태 스트립 |
| #43 | 런처 아이콘 교체 (네이비·옐로 새싹 디자인) |
| #45 | UI 전체를 네이비·옐로·새싹 브랜드 팔레트로 통일 |
| #47 | 주행 화면 정리(히어로 스타일 제거), 얼굴 링·대기 칩 색 수정 |
| #49 | 추가 코칭 3종: 깜빡이 끄기 잊음, 회전 중 과속, 움직이는 중 기어 변경 |

### 11/12 — 다듬기와 시연 준비
| # | 작업 |
|---|------|
| #51 | 주행 시작을 "실제로 움직일 때"로, P 후 60초 안에 출발하면 같은 주행으로 이어가기 |
| #53 | 새 코칭 유형을 성적표 점수·과제에 반영 |
| #55 | 대시보드에 새 통계 표시, Asta Sans가 이미 시스템 기본 서체임을 확인·문서화 |
| #57 | 한쪽 조향 후 복귀형 차선 변경 인식, 회전 후 핸들 푸는 구간 오탐 제거 |
| #59 | 자주 나오는 코칭 클립 11개를 녹음본으로 교체 |
| #61 | 시연 시나리오 자동 재생 스크립트, 기어 경고 반복·성적표 음성 누락 수정 |

---

## 3. 사용한 SDK와 쓰임새

### 3.1 Pleos Vehicle SDK (`ai.pleos.playground:Vehicle:2.3.2`) — `PleosVehicleDataSource`

| 가져오는 정보 | API | 권한 | 어디에 썼나 |
|---------------|-----|------|-------------|
| 조향각 (°, 왼쪽 +) | `SteeringWheel.registerSteeringWheelAngle` | `pleos.car.permission.READ_CAR_STEERING` | 차선 변경 인식(깜빡이 누락·칭찬), 회전 중 과속, 깜빡이 끄기 잊음, 주차 코치 핸들 단계(끝까지·가운데) 확인, 대시보드 조향 막대 |
| 깜빡이 상태 (OFF/LEFT/RIGHT) | `TurnSignal.registerTurnSignalState` | `pleos.car.permission.CAR_LIGHTS` | 깜빡이 누락 판정(조향 3초 전부터 켜졌는지), 깜빡이 끄기 잊음, 대시보드 깜빡이 화살표 |
| 브레이크 페달 (Boolean) | `Brake.registerBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` | 급제동 판정(감속도와 함께), 대시보드 표시 |

- `Vehicle.initialize()`는 반환값 없음, 연결 전 등록은 SDK가 대기열로 처리 (sources.jar로 확인, 문서와 다름)
- 비상등 중에는 TurnSignal이 OFF로 보고됨

### 3.2 AAOS CarPropertyManager (`android.car`) — `AaosPropertyDataSource`

| 가져오는 정보 | 프로퍼티 | 권한 | 어디에 썼나 |
|---------------|----------|------|-------------|
| 기어 (P/R/N/D) | `GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` | 주행 시작·종료(성적표), 주차 코치 시작(D/N→R)·종료(P), 움직이는 중 기어 변경, 화면 모드 전환, 디텍터 D단 조건 |
| 차속 (km/h) | `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` (위치 권한 팝업) | 급제동 감속도, 회전 과속, 기어 변경 판정, 주행 시작(3km/h 초과)·거리·최고 속도, 가상 경로 거리 적분(상황 예고), 주차 기준점(정지)·과속 |
| 주차 브레이크 | `PARKING_BRAKE_ON` | `CAR_POWERTRAIN` | 대시보드 표시만 (콘솔에서 조작 불가) |
| 비상등 | `HAZARD_LIGHTS_STATE` | `CAR_EXTERIOR_LIGHTS` (signature → **선언 안 함**) | 수신 불가, 화면에 "권한 없음" 표시 |

### 3.3 AAOS CarUxRestrictionsManager — `AaosUxRestrictionSource`

- `registerListener` / `currentCarUxRestrictions`로 주행 규제 상태 수신
- `UxModeController`가 규제 + 기어로 화면 모드 결정: 주행 중(큰 코칭 카드만, 음성 위주) / 정차 / 주차(전체 대시보드)
- 규제 정보를 못 받으면 가장 엄격한 "주행 중"으로 취급

### 3.4 음성 — Android 표준

| API | 어디에 썼나 |
|-----|-------------|
| `MediaPlayer` (`res/raw` 클립) | 모든 코칭·예고·주차·성적표·도움말 문구 재생 (1순위) |
| `TextToSpeech` | 클립이 없을 때 fallback. 에뮬레이터는 표준 엔진이 없어 Pleos 엔진을 `queryIntentServices`로 직접 찾음 |
| `AudioManager` + `AudioFocusRequest` | `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, `GAIN_TRANSIENT_MAY_DUCK`로 다른 오디오를 줄이고 말함. 포커스 잃으면 즉시 정지 |

### 3.5 Gleo (차량 음성 AI)

- `assets/schema.json`에 activity 액션 선언 → Gleo가 Intent(`ACTION_PARAM` JSON)로 앱 호출
- `START_DRIVING_COACH`: 코칭 서비스 시작 / `ASK_CAR_TIP`(topic): 차량 기능 도움말을 앱이 음성으로 답변
- 결과는 `APP_ACTION_RESPONSE` 브로드캐스트로 회신 (도움말은 앱이 직접 말하므로 silent)

### 3.6 기타

- **Foreground Service** (`specialUse`): 화면을 숨겨도 신호 구독·판정·음성 유지, 알림에 "코칭 끄기"
- **SharedPreferences**: 주행 세션(통계·중재자·주행·예고 상태) JSON 저장·복원, 주차 유형 저장
- **Virtual Console HTTP API** (에뮬레이터 전용): `scripts/demo_scenario.py`가 차속·페달·조향·기어·깜빡이를 조작해 시연 재생
- 상황 예고용 내비 정보는 **실제 SDK 연동 없음** — `FakeRouteNaviEventSource`(가상 경로)로 대체, NaviHelper 연동은 2차

---

## 4. 구조

```
app/src/main/java/com/jij6303/sidecoach/
├── data/vehicle/   VehicleDataSource 인터페이스 + Pleos SDK·AAOS·Fake 구현
├── data/navi/      NaviEventSource, 시연용 가상 경로
├── data/store/     SessionStore, SessionStateCodec (세션 저장·복원)
├── data/ux/        UxRestrictionSource (CarUxRestrictions + Fake)
├── domain/         디텍터·중재자·주행(trip)·성적표(report)·상황 예고(situation)·주차(parking)·도움말(tips) — 순수 Kotlin
├── gleo/           Gleo 명령 파싱·처리·응답
├── di/             ServiceLocator (Fake/Real 전환)
├── service/        CoachingService (Foreground Service)
├── voice/          VoiceOutput(클립·TTS), AudioFocusController, VoiceCoach
└── ui/             대시보드·주행/주차·성적표·도움말·테마
scripts/
├── generate_voice_clips.ps1   개발용 TTS 클립 생성 (녹음본은 덮어쓰지 않음)
└── demo_scenario.py           Virtual Console 자동 시연
```

설계 원칙
- 판정 로직은 전부 `domain/` 순수 Kotlin, 시간은 스냅샷 timestamp로 받아 JVM 테스트에서 자유롭게 조작
- 데이터 소스는 cold `callbackFlow` — collect = 구독, 취소 = 해제
- 스냅샷과 내비 이벤트를 하나의 흐름으로 `merge`해 중재자 동시 접근 방지
- 음성 대기열은 최신 1개만 유지, TTL 4초 (성적표 요약은 예외)
- 단위 테스트 파일 19개 (디텍터·중재자·주행·성적표·주차·Gleo·세션·음성)

---

## 5. 코칭 판정 요약

| 코칭 | 조건 (요약) | 성적표 감점 |
|------|-------------|-------------|
| 깜빡이 누락 | 차선 변경(S자 또는 한쪽 조향 후 2~8초 내 중립 복귀) 시 3초 전부터 깜빡이 없음. 90° 이상·8초 초과는 회전/커브로 제외 | −10 |
| 급제동 | 1초 감속도 기준 + 브레이크, 감속 시작 속도로 저속 제외, 후진 중 제외 | −10 |
| 회전 중 과속 | \|조향\| ≥ 90° + 30km/h 이상 1초 | −10 |
| 움직이는 중 기어 변경 | 변속 전 0.5초 최고 차속 ≥ 3km/h에서 D↔R, D/R→P | −10 |
| 깜빡이 끄기 잊음 | 조향 복귀 후 10km/h 이상으로 8초 넘게 깜빡이 유지 | −5 |
| 칭찬 | 깜빡이 켠 차선 변경 누적 | — |

다음 연습 과제 우선순위: 기어 변경 → 깜빡이 누락 → 회전 과속 → 급제동 → 깜빡이 끄기 → 차선 변경 연습 → 지금처럼

---

## 6. 에뮬레이터 실측으로 알게 된 것 (중요)

**신호·권한**
- Virtual Console 조향 부호는 SDK와 **반대** (콘솔 −20 → SDK +20). 앱 기준은 왼쪽 +
- `CAR_SPEED`는 위치 권한 그룹 → "위치 정보 접근" 팝업
- 비상등(`CAR_EXTERIOR_LIGHTS`)은 signature|privileged → 서드파티 수신 불가
- 와이퍼(`CAR_WINDOWS`), 공조(`CAR_CLIMATE`)는 승인 미확정 → 사용 안 함
- 콘솔은 기어를 바꾸는 순간 `speed=0`을 먼저 보냄 → 변속 직전 0.5초 최고 차속으로 판단
- 콘솔 BRAKE는 누르는 동안만 true → 주차 기준점은 "움직인 뒤 0.8초 정지"로
- 차속 이벤트는 값이 바뀔 때만 옴 → 가상 경로는 1초 타이머로도 거리 갱신

**시스템 UI**
- 깜빡이를 켜면 측후방 카메라 팝업(`ai.umos.car.evs`, 754×424dp)이 앱 왼쪽 위를 덮음
- R 기어면 후방카메라 화면이 앱 창 전체를 덮고 포커스를 가져감 → 주차 코치는 화면 입력 없이 진행
- 앱 창 1개 = 841×1210dp, 2개 = 1682×1210dp, 160dpi(1dp = 1px)
- 주행 규제는 콘솔 기어로 바뀜 (P면 해제), `hmg_car_service` 명령은 없음
- 시스템 기본 서체가 이미 Asta Sans → 폰트 파일 넣지 않음 (넣으면 APK만 5.7MB 증가)

**음성**
- 에뮬레이터에 표준 TTS 엔진 없음 (Pleos TTS 서비스는 `CATEGORY_DEFAULT` 없음) → 녹음 클립 위주
- `res/raw`에 같은 이름 wav·mp3 공존 시 빌드 실패
- `adb screenrecord`는 소리를 녹음하지 못함 → 시연 영상은 PC에서 창 녹화

**Gleo·기타**
- Gleo는 앱 설치 시 schema.json을 바로 읽음. 실제 음성 호출은 Gleo 검토 후 활성화 → 에뮬레이터는 `am start`로 시뮬레이션
- 앱은 u10 사용자로 실행 → 비정상 종료 재현은 `am crash --user 10 <패키지>`
- Virtual Console HTTP API는 1초마다 `POST /browser/session` 하트비트가 없으면 503

---

## 7. 사용자 결정으로 정한 것

- 숙련도(초급/중급) 구분 없이 교습용 코칭 하나만 (#27)
- 튜토리얼 제거 — 확인 가능한 조작이 깜빡이·브레이크뿐 (#35)
- 차량 버튼 아이콘은 시스템 자산을 뽑지 않고 Canvas로 직접 그림 (#29)
- 승인되지 않은 권한(와이퍼·공조·비상등)이 필요한 기능은 넣지 않음
- 음성은 직접 녹음본으로 단계적 교체 (#39, #59)

---

## 8. 남은 일 / 확인 필요

- 나머지 개발용 TTS 클립(Windows 기본 음성)을 녹음본으로 교체 — 배포 가능 여부 미확인
- FGS 타입 `specialUse` 심사 수용 여부
- Pleos 개발자 사이트 My Project의 schema.json을 현재 버전과 맞추기
- 상황 예고의 실제 NaviHelper 연동 (2차 범위, `NaviEventSource`만 교체)
- 시연 영상 녹화 (`scripts/demo_scenario.py` + PC 창 녹화로 음성 포함)
