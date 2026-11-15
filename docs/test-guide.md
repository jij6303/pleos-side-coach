# 에뮬레이터 테스트 가이드

## 1. 환경 준비

1. **에뮬레이터**: Pleos Connect Emulator v2.1.0 (API 34), RAM 4GB 이상.
   - 반드시 **Cold boot**로 켠다. Quick boot로 켜면 차량 신호(VHAL)가 동작하지 않는다.
2. **앱 설치**: `./gradlew installDebug`
3. **Virtual Console 연결**: 차량 신호를 바꾸는 웹 콘솔이다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저에서 `http://127.0.0.1:8088` 을 연다.
4. 앱을 처음 실행하면 "위치 정보 접근" 권한을 묻는다. 차속을 읽는 권한이 위치 권한 그룹에 묶여 있어서 그렇다. 허용한다.

## 2. 차량 신호 조작 (Virtual Console)

| 하고 싶은 것 | 콘솔 조작 |
|---|---|
| 주행 시작 | 기어 **D** + **ACCEL** 버튼을 누르고 있기 (차속이 오른다) |
| 감속·정지 | **BRAKE** 버튼 (누르는 동안만 브레이크가 켜진다) |
| 차선 변경 | 조향 슬라이더를 한쪽으로 움직였다가 0으로 |
| 회전 | 조향 슬라이더를 90° 이상 |
| 깜빡이 | Turn Signal: Blink Left / Blink Right / Off |
| 주행 종료 → 성적표 | 정지 후 기어 **P** |
| 주차 코치 | 주행 중(D) 기어 **R** → ACCEL 짧게 → BRAKE로 정지 → 조향 → 반복 → P |

알아둘 점
- **조향 부호가 반대다.** 콘솔에서 −20을 주면 앱은 +20(왼쪽)으로 받는다. 앱 기준은 왼쪽 +, 오른쪽 −.
- **주행 규제는 기어로 바뀐다.** P면 정차 화면(대시보드), 그 외 기어면 주행 화면(코칭 카드)이 된다.
- 깜빡이를 켜면 시스템 측후방 카메라 팝업이, R 기어에서는 후방카메라 화면이 앱을 덮는다. 정상 동작이다.
- 비상등은 권한이 없어 앱에서 읽지 못한다. 대시보드에 "권한 없음"으로 나온다.

## 3. 상황 예고 확인

- **시연 경로**: 내비 경로 안내가 없으면, 기어가 P를 벗어날 때 가상 경로가 시작된다. 400m 회전교차로, 1.1km 어린이보호구역, 1.8km 비보호좌회전, 2.6km 합류 순서다. 콘솔 차속으로 이동 거리를 계산하므로 D + ACCEL로 달리면 예고가 나온다. 대시보드에 "시연 경로" 칩이 보인다.
- **실제 내비 경로**: 에뮬레이터 내비에서 목적지를 정해 경로 안내를 시작하면 실제 회전 목록으로 차로 안내가 나온다. 에뮬레이터 내비는 위치가 움직이지 않으므로, 남은 거리는 콘솔 차속으로 계산한다.
- P에 넣었다가 60초 안에 다시 출발하면 같은 주행으로 이어가므로 이미 한 예고는 다시 나오지 않는다. 처음부터 다시 들으려면 P에서 60초 넘게 기다린다.

## 4. Gleo 음성 명령 시뮬레이션

에뮬레이터에서는 Gleo 실제 음성 호출이 동작하지 않으므로 adb로 같은 Intent를 보낸다.

```bash
# 코칭 시작
adb shell am start -a START_DRIVING_COACH -n com.jij6303.sidecoach/.app.MainActivity

# 지금 2차로
adb shell am start -a SET_CURRENT_LANE -n com.jij6303.sidecoach/.app.MainActivity --es ACTION_PARAM '{"laneNumber":2}'

# 차량 기능 질문 (topic은 feature/tips/CarTip.kt의 이름: WINDSHIELD_FOG, REGEN_BRAKING 등)
adb shell am start -a ASK_CAR_TIP -n com.jij6303.sidecoach/.app.MainActivity --es ACTION_PARAM '{"topic":"WINDSHIELD_FOG"}'
```

## 5. 로그와 상태 확인

```bash
adb logcat -s Coach Navi Parking Gleo    # 코칭 메시지, 내비 이벤트, 주차 코치, Gleo 명령 로그
adb logcat -b crash                      # 크래시
adb exec-out screencap -p > screen.png   # 화면 캡처
adb shell am crash --user 10 com.jij6303.sidecoach   # 비정상 종료 → 세션 복원 확인 (앱은 사용자 10으로 실행됨)
```

## 6. 주요 확인 항목

| 시나리오 | 조작 | 기대 결과 |
|---|---|---|
| 깜빡이 누락 | D, 30km/h 이상, 깜빡이 Off, 조향 한쪽 → 0 | "깜빡이 먼저" 음성, 성적표 누락 +1 |
| 깜빡이 정상 | 깜빡이를 먼저 켜고 같은 조작 | 음성 없음, 사용률 증가, 쌓이면 칭찬 |
| 급제동 | 60km/h에서 BRAKE를 길게 | 급제동 음성, 잠시 동안 반복 없음 |
| 회전 과속 | 30km/h 이상에서 조향 90° 이상 | 회전 과속 음성 |
| 기어 변경 | 움직이는 중 D → P | 기어 경고 음성 |
| 상황 예고 | 시연 경로로 400m 주행 | 회전교차로 예고 1회 |
| 화면 전환 | 기어 P ↔ D | 대시보드 ↔ 주행 화면 |
| 성적표 | 주행 후 정지, P | 성적표 화면과 요약 음성 |
| Back 키 | 최상단에서 Back | 앱이 숨겨지고 코칭은 계속 |
| 주차 코치 | D → R, 단계별 조향·정지 | 단계 안내, 조향 확인, 마지막에 "주차 끝" |
| 세션 복원 | 주행 중 `am crash` | 서비스 재시작 후 통계 유지 |
