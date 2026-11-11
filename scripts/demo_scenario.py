"""
시연 시나리오 자동 재생 (#61)

Pleos Virtual Console을 HTTP로 조작해 정해진 주행을 재생한다. 앱은 실제 신호 경로
(콘솔 → VHAL → Pleos SDK / AAOS → 앱)로 값을 받으므로 계기판·카메라 팝업·앱 화면이 함께 움직인다.

준비
  1. 에뮬레이터 실행, 앱 실행 (코칭 서비스 동작 중)
  2. adb forward tcp:8088 tcp:8088
  3. 브라우저의 Virtual Console 탭은 닫는다 (페이지가 자기 값으로 덮어쓴다)
  4. 손으로 주행한 직후라면 P에 넣고 1분 뒤에 실행한다 (앱이 같은 주행으로 이어가 예고가 안 나옴, #51)
     스크립트로 재생한 직후라면 남은 시간만큼 알아서 기다린다

실행
  python scripts/demo_scenario.py            # 재생
  python scripts/demo_scenario.py --record   # 재생하면서 화면 녹화 → demo_YYYYmmdd_HHMMSS.mp4
                                             # adb screenrecord는 소리가 녹음되지 않는다(Android 제약).
                                             # 음성까지 담으려면 PC에서 OBS·Windows 게임 바(Win+G)로 에뮬레이터 창을 녹화한다
  python scripts/demo_scenario.py --list     # 단계만 출력

조향 값은 앱(SDK) 기준으로 적는다: 왼쪽 +, 오른쪽 −. 콘솔은 부호가 반대라 보낼 때 뒤집는다(실측 #9).
콘솔 API(실측 #61): 1초마다 POST /browser/session 하트비트가 없으면 503(bridge_ready=false).
"""

import argparse
import os
import subprocess
import tempfile
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime

BASE = "http://127.0.0.1:8088"
TICK = 0.15  # 조작값 재전송 간격(초)

# 앱은 P에 넣고 60초 안에 다시 출발하면 같은 주행으로 이어간다(#51). 그러면 상황 예고가 '이미 말함'으로 남아
# 나오지 않으므로, 직전 재생의 P 시각을 기록해 두고 61초가 지나야 시작한다.
RESUME_WINDOW_S = 61
LAST_PARK_FILE = os.path.join(tempfile.gettempdir(), "sidecoach_demo_last_park")


# ───────────────────────── 콘솔 HTTP ─────────────────────────

def _request(method: str, path: str) -> int:
    req = urllib.request.Request(BASE + path, method=method, data=b"" if method in ("POST", "PUT") else None)
    try:
        with urllib.request.urlopen(req, timeout=3) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


class Console:
    """현재 조작값을 들고 있다가 [TICK]마다 다시 보낸다. 하트비트도 함께 보낸다."""

    def __init__(self):
        self.client = "sidecoach-demo-" + uuid.uuid4().hex[:8]
        self.speed = 0.0
        self.accel = 0
        self.brake = 0
        self.steering = 0.0  # SDK 기준
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._threads = []

    def start(self):
        self._heartbeat()
        for target in (self._heartbeat_loop, self._controls_loop):
            t = threading.Thread(target=target, daemon=True)
            t.start()
            self._threads.append(t)
        time.sleep(1.5)  # 브리지가 준비될 시간

    def stop(self):
        self._stop.set()
        for t in self._threads:  # 세션을 닫기 전에 재전송을 멈춘다 (닫은 뒤 보내면 503)
            t.join(timeout=2)
        _request("POST", f"/browser/session?client={self.client}&closing=1")

    def _heartbeat(self):
        _request("POST", f"/browser/session?client={self.client}")

    def _heartbeat_loop(self):
        while not self._stop.wait(1.0):
            self._heartbeat()

    def _controls_loop(self):
        while not self._stop.wait(TICK):
            self.send_controls()

    def send_controls(self):
        with self._lock:
            q = (f"speed={round(self.speed)}&accelerator={self.accel}&brake={self.brake}"
                 f"&steering={round(-self.steering)}")
        status = _request("PUT", f"/vehicle/controls?{q}")
        if status != 200:
            print(f"  ! controls HTTP {status}", file=sys.stderr)

    def set(self, **kw):
        with self._lock:
            for k, v in kw.items():
                setattr(self, k, v)

    def gear(self, g: str):
        status = _request("PUT", f"/vehicle/gear?gear={g}")
        if status != 200:
            print(f"  ! gear HTTP {status}", file=sys.stderr)

    def signal(self, mode: str):
        status = _request("PUT", f"/vehicle/turn-signal?mode={mode}")
        if status != 200:
            print(f"  ! turn-signal HTTP {status}", file=sys.stderr)


# ───────────────────────── 동작 단위 ─────────────────────────

class Player:
    def __init__(self, console: Console):
        self.c = console
        self.t0 = time.monotonic()

    def log(self, text: str):
        print(f"[{time.monotonic() - self.t0:6.1f}s] {text}", flush=True)

    def wait(self, sec: float):
        time.sleep(sec)

    def ramp(self, sec: float, speed=None, steering=None, accel=0, brake=0):
        """[sec]초 동안 차속·조향을 목표값까지 선형으로 바꾼다."""
        s0, a0 = self.c.speed, self.c.steering
        s1 = s0 if speed is None else speed
        a1 = a0 if steering is None else steering
        steps = max(1, int(sec / TICK))
        for i in range(1, steps + 1):
            f = i / steps
            self.c.set(speed=s0 + (s1 - s0) * f, steering=a0 + (a1 - a0) * f, accel=accel, brake=brake)
            self.c.send_controls()
            time.sleep(sec / steps)
        self.c.set(accel=0, brake=0)

    def lane_change(self, direction: str, signal: bool, angle=30.0, hold=3.0):
        """한쪽으로 꺾었다 돌아오는 차선 변경. direction: 'left' | 'right'."""
        sdk = angle if direction == "left" else -angle
        mode = "BlinkLeft" if direction == "left" else "BlinkRight"
        if signal:
            self.c.signal(mode)
            self.wait(1.5)
        self.ramp(0.6, steering=sdk)
        self.wait(hold)
        self.ramp(0.6, steering=0)
        if signal:
            self.wait(0.8)
            self.c.signal("Off")


# ───────────────────────── 시나리오 ─────────────────────────
# (설명, 기대 음성, 동작). 기대 음성은 진행 로그에만 쓴다.
# 가짜 경로 예고 위치: 출발 후 100m 회전교차로, 800m 어린이보호구역, 1500m 비보호좌회전, 2300m 합류.

def scenario(p: Player):
    c = p.c
    yield "준비: P, 정차", None, lambda: (c.signal("Off"), c.gear("Park"), c.set(speed=0, steering=0), p.wait(2))
    yield "출발: D, 60km/h까지 가속", "곧 회전교차로예요… (100m 지점)", lambda: (
        c.gear("Drive"), p.wait(1.0), p.ramp(8, speed=60, accel=40), p.wait(10))
    yield "오른쪽 차선 변경 (깜빡이 켬) 1/3", None, lambda: (p.lane_change("right", True), p.wait(4))
    yield "왼쪽 차선 변경 (깜빡이 켬) 2/3", None, lambda: (p.lane_change("left", True), p.wait(4))
    yield "오른쪽 차선 변경 (깜빡이 켬) 3/3", "깜빡이 아주 잘 켜고 있어요. 좋아요!", lambda: (
        p.lane_change("right", True), p.wait(8))
    yield "어린이보호구역 접근, 30km/h로 감속", "곧 어린이보호구역이에요… (800m 지점)", lambda: (
        p.wait(2), p.ramp(6, speed=30, brake=20), p.wait(8))
    yield "구역 통과, 50km/h로 가속", None, lambda: (p.ramp(5, speed=50, accel=30), p.wait(3))
    yield "왼쪽 차선 변경 (깜빡이 없음)", "차선 바꿀 땐 깜빡이 먼저 켜요!", lambda: (
        p.lane_change("left", False), p.wait(6))
    # 비보호좌회전 예고(1500m)가 끝난 뒤에 깜빡이 끄기 경고가 나오도록 먼저 기다린다
    yield "비보호좌회전 지점 접근", "곧 비보호좌회전이에요… (1500m 지점)", lambda: p.wait(14)
    yield "오른쪽 차선 변경 후 깜빡이를 켜 둔 채 주행", "깜빡이가 아직 켜져 있어요. 꺼 주세요.", lambda: (
        c.signal("BlinkRight"), p.wait(1.5), p.ramp(0.6, steering=-30), p.wait(3), p.ramp(0.6, steering=0),
        p.wait(10), c.signal("Off"), p.wait(4))
    yield "40km/h로 크게 회전 (핸들 120°)", "돌 때는 속도를 줄여요. 천천히요.", lambda: (
        p.ramp(3, speed=40, brake=10), p.ramp(1.0, steering=120), p.wait(2.5), p.ramp(1.5, steering=0), p.wait(6))
    yield "60km/h로 가속 후 급제동", "조금 더 일찍, 부드럽게 밟아볼까요?", lambda: (
        p.ramp(5, speed=60, accel=40), p.wait(3), p.ramp(2.0, speed=20, brake=80), p.wait(6))
    # R로 바꾸면 주차 코치가 시작되고 카메라 화면이 덮으므로, 움직이는 중 P로 바꾼다.
    # 앱은 기어 경고를 먼저 말하고 바로 성적표를 띄운다 (#49).
    yield "20km/h로 가다가 멈추지 않고 P로 변경 → 성적표", "기어는 완전히 멈춘 뒤에 바꿔요. → 성적표 음성", lambda: (
        p.wait(2), c.gear("Park"), c.set(speed=0, steering=0), p.wait(12))


# ───────────────────────── 실행 ─────────────────────────

def restore(c: Console):
    c.signal("Off")
    c.set(speed=0, steering=0, accel=0, brake=0)
    c.send_controls()
    c.gear("Park")
    try:
        with open(LAST_PARK_FILE, "w") as f:
            f.write(str(time.time()))
    except OSError:
        pass


def wait_after_last_park(skip: bool):
    """직전 재생에서 P에 넣은 지 61초가 지나지 않았으면 남은 시간만큼 기다린다."""
    try:
        with open(LAST_PARK_FILE) as f:
            elapsed = time.time() - float(f.read().strip())
    except (OSError, ValueError):
        return
    remain = RESUME_WINDOW_S - elapsed
    if remain <= 0 or skip:
        return
    print(f"직전 재생이 끝난 지 {elapsed:.0f}초예요. 새 주행으로 시작하도록 {remain:.0f}초 기다려요 (--no-wait로 건너뛰기)")
    while remain > 0:
        print(f"\r  남은 시간 {remain:4.0f}초", end="", flush=True)
        time.sleep(min(1, remain))
        remain -= 1
    print()


def start_recording():
    remote = "/sdcard/sidecoach_demo.mp4"
    proc = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "180", remote])
    return proc, remote


def stop_recording(proc, remote):
    subprocess.run(["adb", "shell", "pkill", "-INT", "screenrecord"], check=False)
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()
    time.sleep(1)
    local = datetime.now().strftime("demo_%Y%m%d_%H%M%S.mp4")
    subprocess.run(["adb", "pull", remote, local], check=False)
    subprocess.run(["adb", "shell", "rm", remote], check=False)
    print(f"녹화 파일: {local}")


def main():
    # 파이프·Git Bash에서도 한글이 깨지지 않게 (Windows 기본 cp949 대신 UTF-8)
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description="초보운전탈출 시연 시나리오 자동 재생")
    ap.add_argument("--record", action="store_true", help="adb screenrecord로 녹화 (최대 3분)")
    ap.add_argument("--list", action="store_true", help="단계만 출력하고 끝낸다")
    ap.add_argument("--no-wait", action="store_true", help="직전 재생 뒤 61초 대기를 건너뛴다")
    args = ap.parse_args()

    if args.list:
        for i, (desc, voice, _) in enumerate(scenario(Player(Console())), 1):
            print(f"{i:2d}. {desc}" + (f"\n      → {voice}" if voice else ""))
        return

    if _request("GET", "/vehicle/state") != 200:
        sys.exit("Virtual Console에 연결할 수 없어요. 'adb forward tcp:8088 tcp:8088'을 먼저 실행하세요.")

    c = Console()
    c.start()
    c.gear("Park")
    c.signal("Off")
    wait_after_last_park(args.no_wait)
    rec = start_recording() if args.record else None
    p = Player(c)
    try:
        for desc, voice, action in scenario(p):
            p.log(desc + (f"  → 기대: {voice}" if voice else ""))
            action()
        p.log("끝")
    except KeyboardInterrupt:
        p.log("중단")
    finally:
        restore(c)
        c.stop()
        if rec:
            stop_recording(*rec)


if __name__ == "__main__":
    main()
