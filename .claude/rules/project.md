## 프로젝트: 초보운전탈출 (Pleos Side-Coach)

초보운전자용 Pleos Connect(AAOS) 실시간 음성 코칭 앱. 현대차그룹 차량용 앱 공모전 출품작 (1차 마감 2026-11-15).

### 반드시 먼저 읽을 문서
- .claude/reference/dev-architecture.md : 아키텍처, API 명세, 판정 로직, 매니페스트, 테스트 계획. 특히 "0.1 에뮬레이터 실측 결과"가 최우선 기준
- .claude/reference/pleos-dev-guide-summary.md : Pleos 플랫폼 규칙 요약
- docs/architecture.md : 패키지별 책임. 새 코드는 이 책임 구분에 맞는 패키지에 둔다

### 문서 규칙
- `docs/`와 `README.md`는 **사람용**이다. 사용자가 직접 검토한 내용만 둔다
  - 지금 상태만 쓴다. "~는 뺐다", "~에서 바뀌었다", 이슈 번호(#N), 취소선 같은 변경 이력은 쓰지 않는다
  - 사람용 문서를 고칠 때는 고칠 내용을 먼저 보여주고 승인받는다
- `.claude/reference/`는 **Claude용**이다. 실측값, API 시그니처, 작업 기록, 이력은 여기에 쓴다
  - 작업 일지는 `.claude/reference/PROJECT_LOG.md`
- 작업 전에 구현 방법을 먼저 설명하고 사용자가 이해·승인한 뒤 진행한다 (`.claude/reference/feedback.md` 6장)

### 고정값
- 패키지명: com.jij6303.sidecoach (schema.json, Pleos 프로젝트와 동일해야 함)
- minSdk 28, Kotlin, Jetpack Compose, Kotlin DSL
- Vehicle SDK: ai.pleos.playground:Vehicle:2.3.2 (Pleos nexus 저장소)
- 기어/차속/비상등/주차브레이크는 AAOS CarPropertyManager, TTS는 Android 표준 TextToSpeech

### 절대 규칙
- 문서에서 확인되지 않은 Pleos API 이름·시그니처를 지어내지 말 것. 모르면 "확인 필요"로 남기고 물어볼 것
- 승인되지 않은 pleos.car.permission은 선언하지 말 것 (심사 탈락)
- 주행 중(CarUxRestrictions)에는 키보드·영상·애니메이션·전체화면 팝업 금지, 음성 위주
- 최상단 Back은 moveTaskToBack(true), finish() 금지
- 조향각 부호: 왼쪽 +, 오른쪽 −. 브레이크는 Boolean. 비상등 중엔 TurnSignal이 OFF로 보고됨
- 데이터 소스는 인터페이스로 분리하고 Fake 구현을 둘 것 (테스트·데모용)

### 테스트 환경
- 에뮬레이터: Pleos Connect v2.1.0 (API 34), Cold boot
- Virtual Console: adb forward tcp:8088 tcp:8088 → http://127.0.0.1:8088 (차속: 기어 D + ACCEL)