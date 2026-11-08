# Pleos Connect 개발자 가이드 정리 (2026-11-08 기준)

출처: document.pleos.ai. 공모전(현대차그룹 차량용 앱 개발 공모전, 접수 마감 2026-11-15) 기획과 개발에 참고하려고 정리함.

---

## 1. 플랫폼 기본

- AAOS(Android Automotive OS) 기반. 일반 안드로이드 앱(Kotlin/Java, Gradle)으로 개발함.
- 요구 사항: minSdk 28 이상, Gradle 8.0 이상. 샘플 앱은 JDK 17, AGP 8.8.2, Kotlin 2.0.0, compile/targetSdk 35 사용.
- 에뮬레이터
  - Android Studio에 API 34 "Pleos Connect System Image"를 설치함. SDK Update Site URL은 아래 중 하나.
    - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
    - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  - AVD 생성: Form factor는 Automotive, 기기는 Pleos Connect v2.1.0, RAM 4GB, **Cold boot** (quick boot를 쓰면 VHAL이 꺼짐).
  - GPU: 최소 RTX 3060 Laptop 6GB급. RTX 3060 Ti 8GB면 충분함.
- SDK 저장소
  - Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
  - 의존성 예: `implementation("ai.pleos.playground:Vehicle:2.3.1")` (최신 문서는 2.3.2)
  - developers.pleos.ai에 가입하고 My Project를 만들어야 함. Project ID는 심사 때 필요하고, TTS/STT 서버 모드는 Client ID/Secret이 필요함.
- 샘플 저장소: github.com/Pleos-Playground/ 아래 `pleos-sample-vehicle-app`, `pleos-sample-navi-app`, `pleos-sample-fusedlocation-app`

## 2. SDK 5종

| SDK | 버전 | 용도 | 비고 |
|---|---|---|---|
| Vehicle | 2.3.2 | 차량 상태 조회·제어 (19개 도메인) | 모델마다 지원이 다르므로 `checkXXXCapabilities`로 확인 |
| NaviHelper | 3.0.5 | 목적지·경유지·경로 설정, 주행 이벤트 수신 | 내비가 탑재된 차량에서만 동작 |
| TextToSpeech | 3.3.1 | 음성 출력 | 모드는 HYBRID(기본), SERVER, ON_DEVICE. ON_DEVICE는 앱 등록이 필요 없음 |
| SpeechToText | 3.3.1 | 음성을 텍스트로 변환 | 명령 실행은 하지 않음. 명령 실행은 Gleo Interface를 씀 |
| FusedLocation | 1.1.3 | 차량 위치 | `isSupported()`를 먼저 확인. 권한은 `pleos.car.permission.FUSED_LOCATION` |

※ Getting-started 페이지에는 FusedLocation, TTS, STT가 "준비 중"으로 표시돼 있음(Playground 권한 신청 기준으로 보임). 실제로 사용할 수 있는지는 에뮬레이터에서 확인해야 함.

### Vehicle SDK 사용 패턴

- 흐름: `Vehicle` 생성 → `initialize()` → `vehicle.getDoor()` 같은 도메인 객체 획득 → `checkXxxCapabilities`, `getXxx`, `setXxx`, `registerXxx`/`unregisterXxx` 사용 → `release()`
- 결과는 `onComplete`/`onFailed` 비동기 콜백으로 받음. 콜백이 끝나기 전에 `release()`를 호출하면 안 됨.
- 도메인 목록: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light(앰비언트 제어), Odometer, Safety(경고), Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper
- 권한은 `pleos.car.permission.` 뒤에 아래 이름을 붙임. 조회와 제어 권한이 나뉨.
  - 조회: CAR_CLIMATE, CAR_DOORS, CAR_DRIVE, CAR_ENERGY, CAR_INFO, CAR_LIGHTS, CAR_MILEAGE, CAR_MIRRORS, CAR_POWERTRAIN, CAR_SAFETY, CAR_SEATS, CAR_SPEED, CAR_TOLL, CAR_TIRES, CAR_WINDOWS, READ_CAR_DISPLAY_UNITS, READ_CAR_STEERING, READ_ADAS_STATES, READ_WINDSHIELD_WIPERS
  - 제어: CONTROL_CAR_CLIMATE, CONTROL_CAR_DOORS, CONTROL_CAR_DRIVE, CONTROL_CAR_ENERGY, CONTROL_CAR_INFO, CONTROL_CAR_LIGHTS, CONTROL_CAR_MIRRORS, CONTROL_CAR_SAFETY, CONTROL_CAR_SEATS, CONTROL_CAR_TOLL, CONTROL_CAR_WINDOWS, CONTROL_WINDSHIELD_WIPERS
  - 런타임 승인 팝업은 없음. 단, **App Market과 협의해 승인받은 권한만** 선언할 수 있고, 승인되지 않은 권한을 넣으면 심사에서 탈락함.
- 속도나 기어처럼 표준 데이터는 AAOS `CarPropertyManager`를 쓰는 것을 권장함. 예: `PERF_VEHICLE_SPEED`(클러스터와 같은 값). EV 배터리는 `getEvBatteryStateOfCharge`로 읽음.

## 3. Gleo Interface (음성 AI 연동)

- 구조는 function calling 방식임. 앱이 `assets/schema.json`에 기능을 정의하면, 사용자가 말했을 때 Gleo가 Intent로 앱을 호출함.
- schema.json 필드
  - `packageName`, `appDescription`, `version`, `actionCalls`(**최대 3개**)
  - 각 action에는 `type: "function"`, `function.component`(`activity`면 startActivity, `service`면 sendBroadcast), `name`, `description`, `parameters`가 들어감
  - `name`은 대문자와 밑줄로 씀. 예: `ADD_REMINDER`
  - description은 **영어**로, 1~2문장으로 씀. 예시를 넣으면 안 되고, Gleo 동작을 조작하는 문구도 넣으면 안 됨.
  - 파라미터 타입은 string, number, boolean, array 중 하나만 씀. enum은 문자열만 가능하고 null은 쓸 수 없음. `required`에는 필수 파라미터만 넣음.
- Manifest: action마다 intent-filter를 선언함. `<action android:name="ADD_REMINDER"/>`와 `category.DEFAULT`를 넣음.
- 파라미터 수신: Intent extra `"ACTION_PARAM"`에 담긴 JSON 문자열을 읽음.
- 응답(Callback)
  - broadcast action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`를 보냄. extras는 `packageName`과 `response`(JSON).
  - response 필드: `functionName`, `status`(success, asking, fail), `message`(영어 완전한 문장), `mode`(voice, beep, silent), `data`(JSON 배열 문자열, 권장)
  - **7초 안에 응답**해야 함. 7초가 지나면 취소됨.
- Gleo 측 검토를 통과하면 연동이 자동으로 활성화됨. 스키마는 부팅할 때와 설치할 때 스캔함.

## 4. 주행 안전 / 주행 규제

- 주행 상태는 Parking, Idling, Driving으로 나뉨. 앱이 **DO(Distraction Optimized)를 선언하지 않으면 주행 중에는 사용할 수 없음.**
- 규제 감지는 `CarUxRestrictionsManager.registerListener()`와 `onUxRestrictionsChanged()`로 함. `getActiveRestrictions()`의 비트 플래그로 판단함.
  - 플래그: NO_DIALPAD=1, NO_FILTERING=2, LIMIT_STRING_LENGTH=4, NO_KEYBOARD=8, NO_VIDEO=16, LIMIT_CONTENT=32, NO_SETUP=64
- 주행 중 전 지역에서 금지: 동영상, 차량 설정 변경(디지털키, 도어, 시트), QR 로그인, 대용량 텍스트
- 북미는 키보드와 다이얼패드도 금지됨. 검색은 음성으로만 가능함. 한국 같은 "그 외" 지역은 키보드와 검색이 허용됨.
- 속도 임계값은 앱이 직접 정하지 않음. 플랫폼이 내려주는 신호를 따름.
- 테스트할 때 쓰는 ADB 명령: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on` (끌 때는 `off`)
- 주행 중 게임, 영상, 광고, 애니메이션, 자동 스크롤은 금지됨. 메뉴는 3 depth 이하로 둬야 하고, 전체 화면 팝업도 금지됨.

## 5. UI/UX 가이드

- 화면 구성: Status bar, Driving view(고정 영역이며 가릴 수 없음), GNB, 앱 window(1개 또는 2개)
- 기본 방향은 portrait(window 1개)임. `screenOrientation="landscape"`로 지정하면 window 2개 너비를 씀. **두 레이아웃을 모두 설계해야 함.** 사용자가 window 크기를 바꿔도 동작해야 함.
- 디스플레이 종류: Connect-S(17", 14.6", 12.9"), Connect-W(와이드, Extended 있음), Connect-WF
- 정보는 1~2초 안에 읽혀야 함(Glanceability). 입력에 대한 응답은 0.25초 이내여야 하고, 늦어지면 로딩 상태를 표시함.
- 대비 4.5:1 이상. 글자는 최소 12sp, 줄간격 1.5배.
- 문장 길이: 한 줄 80자 이하, 3줄 이하. 영어는 120자 또는 24단어 이하.
- 중요한 텍스트는 높이 5mm 이상, 보조 텍스트는 3mm 이상.
- 서체는 Asta Sans(Google Fonts). 한 손으로 조작할 수 있어야 하고, 아이콘에는 텍스트 라벨을 붙임. 주간/야간 모드를 지원함. 광고는 금지됨.
- 텍스트 입력 대신 프리셋, 최근 항목, 음성 입력을 우선함.
- 카테고리 8종: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication

## 6. 앱 동작과 리소스

- 백그라운드로 가면 화면 갱신, 애니메이션, 센서 수집을 멈춤. 프로세스가 재생성될 수 있으니 상태를 저장하고 복원해야 함.
- CPU: 주행 중 비내비 앱은 5% 미만, 내비 앱은 15% 이하, 정차 중에는 25% 이하. RAM은 400MB 이하.
- 네트워크: 캐시, 재시도, 타임아웃 처리를 넣고 오프라인 UI를 제공함.
- 전화, SMS, 자이로, 근접 센서, 캘린더, 주소록 API는 없을 수 있음. 이런 기능은 Optional로 설계함.
- CCP(로터리 컨트롤러): 일반 설치 앱은 지원하지 않아도 됨. 미지원이면 `<meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false"/>`를 넣음.
- **Back 처리**: 최상단 화면에서 Back이 눌리면 `moveTaskToBack(true)`로 앱을 숨김. `finish()`를 쓰면 안 되고, 쓰면 반려 대상임.
- 오디오 포커스: 재생 전에 `AudioFocusRequest`를 요청함. 포커스 이벤트는 이렇게 처리함.
  - LOSS: 정지
  - LOSS_TRANSIENT: 일시정지
  - CAN_DUCK: 볼륨 낮춤
  - GAIN: 재개
- 내비 음성은 `MAY_DUCK`으로 요청함. Suspend(시동 OFF)에 들어가면 재생 정지, 포커스 반납, 상태 저장을 하고, Resume 때 자동으로 재생하지 않음.
- 음성 안내 앞에는 **150~200ms 무음**을 넣음.
- Music/Audio 앱은 `android:appCategory="audio"`를 지정하고 MediaBrowserService와 MediaSession을 반드시 구현함. 자동 재생은 금지됨.
- Navigation 앱은 `category.APP_MAPS`를 선언하고 `CarAppFocusManager`로 단일 경로 정책을 지킴.
- 플랫폼 식별: `Build.DEVICE`로 CONNECT_S, L, W를 구분함(실차에서만 가능). 지역은 `product.sw.target_region`(KOR, EUR, USA, CAN)으로 확인함.

## 7. 디버깅용 ADB

- 설치: `adb install -d -r app.apk`
- 실행: `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1`
- 스크린샷: `adb exec-out screencap -p > screen.png`
- 녹화: `adb exec-out screenrecord --output-format=h264 - > screen.h264` 후 ffmpeg로 mp4 변환. 시연 영상 만들 때 씀.
- 기어 변경은 Virtual Console에서 함.
- 폼 팩터 테스트: FormFactorConsole 앱과 host-display-bridge(Go 1.22 이상)로 여러 화면 크기를 시뮬레이션함.

## 8. App Market 심사 핵심

- 거절 사유 영역 5가지: 콘텐츠 안전성, 기능과 안정성, 운전자 주의 분산, 등록정보 정확성, 개인정보와 보안
- 제출물
  - APK 600MB 이하
  - 아이콘 512×512 PNG
  - IVI 스크린샷 최대 8장. 규격은 2560×1380, 1700×1212, 840×121
  - 개인정보처리방침 URL. 위치정보를 쓰면 위치정보 이용약관도 냄
  - IARC 연령등급
- 승인받지 않은 SDK 권한, 불필요한 권한, 크래시, 화면 잘림, 주행 중 광고나 영상은 반려됨.

## 9. 공모전 관점 시사점

- **차량특화성**: Vehicle SDK 데이터(주행 상태, 속도, 배터리, 도어, 공조, 경고 등)와 주행/정차 맥락을 핵심 기능에 엮어야 점수가 남.
- **기술 활용성**: Vehicle SDK, Gleo Interface(음성 명령 3개), TTS를 조합하면 SDK 활용도를 보여주기 좋음.
- 주행 중 UI와 정차 중 UI를 나누는 설계(CarUxRestrictions 기반)를 기획서에 명시하면 가이드를 이해하고 있다는 걸 어필할 수 있음.
- 게임이나 영상 성격의 기능은 정차 중이나 동승자용으로만 둬야 함.
