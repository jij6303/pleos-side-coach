# 초보운전탈출 (Pleos Side-Coach)

초보운전자 옆자리에 연수 강사가 앉아 있는 것처럼, 차량 신호를 읽고 **지금 무엇을 해야 하는지 음성으로 알려주는** Pleos Connect(AAOS) 앱이다.
현대자동차그룹 차량용 앱 개발 공모전 출품작.

## 주요 기능

- 실시간 조작 코칭: 깜빡이 누락, 급제동, 회전 과속 등을 그 자리에서 교정
- 상황 예고: 회전 전 차로 준비, 연속 회전, 회전교차로 규칙
- 주차 코치: 후진·평행 주차 단계 안내
- 성적표: 주행 후 점수와 다음 연습 과제
- 차량 기능 도움말: 김 서림, 회생제동 같은 버튼 사용법
- Gleo 음성 명령

주행 중에는 음성만 쓰고, 화면 조작은 정차 중에만 한다. 자세한 내용은 [docs/concept.md](docs/concept.md).

## 빌드와 실행

- 요구 사항: Android Studio, JDK 17, Pleos Connect 에뮬레이터 v2.1.0 (API 34)
- Pleos SDK는 `nexus-playground.pleos.ai` 저장소에서 받는다 (`settings.gradle.kts`에 등록됨)

```bash
./gradlew assembleDebug          # APK 빌드
./gradlew installDebug           # 에뮬레이터에 설치
./gradlew testDebugUnitTest      # 단위 테스트
```

에뮬레이터 준비, 차량 신호 조작, 시연 스크립트는 [docs/test-guide.md](docs/test-guide.md)에 있다.

## 어디서 무엇을 보나

| 보고 싶은 것 | 위치 |
|---|---|
| 무엇을 왜 만드는지 | [docs/concept.md](docs/concept.md) |
| 코드 구조와 각 패키지의 책임 | [docs/architecture.md](docs/architecture.md) |
| 에뮬레이터로 테스트·시연하는 법 | [docs/test-guide.md](docs/test-guide.md) |
| 앱 코드 | `app/src/main/java/com/jij6303/sidecoach/` |
| 녹음 음성 클립 | `app/src/main/res/raw/` |
| Gleo 음성 명령 정의 | `app/src/main/assets/schema.json` |
| 권한, 서비스, Gleo 명령 수신 설정 | `app/src/main/AndroidManifest.xml` |
| 시연 자동 재생, 음성 클립 생성 | `scripts/` |
| GitHub 작업 봇 (이슈·브랜치·PR) | `github/` |
| Claude 작업 규칙과 참고 자료 | `CLAUDE.md`, `.claude/` |

`docs/`는 사람이 읽는 문서, `.claude/`는 Claude가 작업할 때 읽는 문서(실측 기록, API 명세, 작업 일지)다.
